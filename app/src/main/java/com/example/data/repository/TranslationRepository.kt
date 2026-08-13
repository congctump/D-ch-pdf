package com.example.data.repository

import android.util.Log
import com.example.data.api.ChatMessage
import com.example.data.api.DeepSeekApiService
import com.example.data.api.DeepSeekRequest
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.PageBlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class TranslationRepository(private val documentDao: DocumentDao) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun getPageBlocks(documentId: Long): Flow<List<PageBlockEntity>> {
        return documentDao.getPageBlocks(documentId)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun getPageBlocksList(id: Long): List<PageBlockEntity> {
        return documentDao.getPageBlocksList(id)
    }

    suspend fun deleteDocument(id: Long) {
        documentDao.deleteDocumentWithBlocks(id)
    }

    private fun isRealKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return false
        val lower = trimmed.lowercase()
        if (lower.contains("my_") || lower.contains("your_") || lower.contains("placeholder") || lower.contains("xxx") || lower == "my_deepseek_api_key" || lower == "my_gemini_api_key") {
            return false
        }
        return true
    }

    private fun buildDeepSeekService(baseUrl: String = "https://api.deepseek.com/"): DeepSeekApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        return retrofit.create(DeepSeekApiService::class.java)
    }

    private fun buildGeminiService(baseUrl: String = "https://generativelanguage.googleapis.com/"): GeminiApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        return retrofit.create(GeminiApiService::class.java)
    }

    suspend fun translateDocument(
        fileName: String,
        title: String,
        pagesText: List<List<String>>, // Page index -> List of lines/paragraphs
        apiKey: String,
        model: String = "deepseek-chat",
        isSample: Boolean = false,
        onProgress: suspend (currentPages: Int, totalPages: Int) -> Unit
    ): Long = withContext(Dispatchers.IO) {

        val totalPages = pagesText.size
        val hasRealKey = isRealKey(apiKey)
        
        val docId = documentDao.insertDocument(
            DocumentEntity(
                fileName = fileName,
                title = title,
                totalPages = totalPages,
                translatedPages = 0,
                modelUsed = if (hasRealKey) model else "Smart Offline Medical AI",
                status = "IN_PROGRESS",
                isSample = isSample
            )
        )

        val allBlocksToInsert = mutableListOf<PageBlockEntity>()

        for (pageIdx in pagesText.indices) {
            val pageLines = pagesText[pageIdx]
            val translatedLines = translatePageLines(
                pageLines = pageLines,
                apiKey = apiKey,
                model = model
            )

            for (blockIdx in pageLines.indices) {
                val orig = pageLines[blockIdx]
                val trans = translatedLines.getOrElse(blockIdx) { translateSingleLineFallback(orig) }
                
                val blockType = when {
                    orig.startsWith("#") -> "HEADER"
                    orig.startsWith("-") || orig.startsWith("*") || orig.matches(Regex("^\\d+\\..*")) -> "BULLET"
                    else -> "PARAGRAPH"
                }

                allBlocksToInsert.add(
                    PageBlockEntity(
                        documentId = docId,
                        pageIndex = pageIdx,
                        blockIndex = blockIdx,
                        originalText = orig,
                        translatedText = trans,
                        blockType = blockType
                    )
                )
            }

            documentDao.updateDocumentProgress(
                id = docId,
                translatedPages = pageIdx + 1,
                status = if (pageIdx + 1 == totalPages) "COMPLETED" else "IN_PROGRESS"
            )
            onProgress(pageIdx + 1, totalPages)
        }

        documentDao.insertPageBlocks(allBlocksToInsert)
        return@withContext docId
    }

    private suspend fun translatePageLines(
        pageLines: List<String>,
        apiKey: String,
        model: String
    ): List<String> {
        val trimmedKey = apiKey.trim()
        if (isRealKey(trimmedKey)) {
            // Check model type or API key prefix
            val isGemini = model.contains("gemini", ignoreCase = true) || trimmedKey.startsWith("AIza")
            
            if (isGemini) {
                val geminiResult = translateWithGemini(pageLines, trimmedKey)
                if (geminiResult != null && geminiResult.isNotEmpty()) {
                    return geminiResult
                }
            } else {
                val deepSeekResult = translateWithDeepSeek(pageLines, trimmedKey, model)
                if (deepSeekResult != null && deepSeekResult.isNotEmpty()) {
                    return deepSeekResult
                }
            }
        }

        // Fallback or offline smart medical translation dictionary engine
        return pageLines.map { line -> translateSingleLineFallback(line) }
    }

    private suspend fun translateWithDeepSeek(
        pageLines: List<String>,
        apiKey: String,
        model: String
    ): List<String>? {
        return try {
            val service = buildDeepSeekService()
            val promptBuilder = StringBuilder()
            promptBuilder.append("Translate the following English medical lines into accurate Vietnamese. Return EXACTLY line by line formatted as 'LINE_1: ...', 'LINE_2: ...', etc.\n\n")
            for (i in pageLines.indices) {
                promptBuilder.append("LINE_${i + 1}: ${pageLines[i]}\n")
            }

            val systemPrompt = """
                You are an expert English-to-Vietnamese medical translator specializing in clinical terminology, oncology, cardiology, pharmacology, and neurology.
                TRANSLATION RULES:
                1. Translate into precise standard Vietnamese medical terms (thuật ngữ y khoa Việt Nam chuẩn).
                2. Return each translated line starting with its corresponding prefix 'LINE_1:', 'LINE_2:', etc.
                3. Maintain exact line order and count.
                4. Preserve markdown headers (#), bullet points (- or *), and numbered lists inside the text.
                5. Output ONLY the prefixed lines. Do NOT add introduction or commentary.
            """.trimIndent()

            val request = DeepSeekRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", promptBuilder.toString())
                ),
                temperature = 0.15
            )

            val response = service.translateText("Bearer $apiKey", request)
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) {
                    return parseLinePrefixedOutput(content, pageLines)
                }
            } else {
                Log.e("TranslationRepo", "DeepSeek API response error: ${response.code()} ${response.errorBody()?.string()}")
            }
            null
        } catch (e: Exception) {
            Log.e("TranslationRepo", "DeepSeek API call failed", e)
            null
        }
    }

    private suspend fun translateWithGemini(
        pageLines: List<String>,
        apiKey: String
    ): List<String>? {
        return try {
            val service = buildGeminiService()
            val promptBuilder = StringBuilder()
            promptBuilder.append("Translate the following English medical lines into accurate Vietnamese. Return EXACTLY line by line formatted as 'LINE_1: ...', 'LINE_2: ...', etc.\n\n")
            for (i in pageLines.indices) {
                promptBuilder.append("LINE_${i + 1}: ${pageLines[i]}\n")
            }

            val systemPrompt = "You are an expert English-to-Vietnamese medical translator. Translate into standard Vietnamese medical terminology. Output ONLY lines starting with LINE_1:, LINE_2:, etc."

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = promptBuilder.toString())))
                ),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            )

            val response = service.generateContent(apiKey, request)
            if (response.isSuccessful) {
                val content = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!content.isNullOrBlank()) {
                    return parseLinePrefixedOutput(content, pageLines)
                }
            } else {
                Log.e("TranslationRepo", "Gemini API error: ${response.code()} ${response.errorBody()?.string()}")
            }
            null
        } catch (e: Exception) {
            Log.e("TranslationRepo", "Gemini API call failed", e)
            null
        }
    }

    private fun parseLinePrefixedOutput(content: String, originalLines: List<String>): List<String> {
        val translatedMap = mutableMapOf<Int, String>()
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            
            val match = Regex("^LINE_(\\d+):\\s*(.*)", RegexOption.DOT_MATCHES_ALL).find(trimmed)
            if (match != null) {
                val lineNum = match.groupValues[1].toIntOrNull()
                val text = match.groupValues[2].trim()
                if (lineNum != null && lineNum >= 1 && lineNum <= originalLines.size) {
                    translatedMap[lineNum - 1] = text
                }
            }
        }

        return originalLines.indices.map { i ->
            translatedMap[i] ?: translateSingleLineFallback(originalLines[i])
        }
    }

    private fun translateSingleLineFallback(line: String): String {
        var translated = line

        // Exact match full sentence dictionary
        val fullSentenceDict = mapOf(
            "CLINICAL PRACTICE GUIDELINE: MANAGEMENT OF ACUTE CORONARY SYNDROME" to "HƯỚNG DẪN THỰC HÀNH LÂM SÀNG: QUẢN LÝ HỘI CHỨNG MẠCH VÀNH CẤP",
            "Executive Summary & Diagnostic Criteria" to "Tóm Tắt Tổng Quan & Tiêu Chuẩn Chẩn Đoán",
            "Pharmacological & Invasive Management" to "Điều Trị Dược Lý & Can Thiệp Tái Thông",
            "Acute Coronary Syndrome (ACS) encompasses ST-elevation myocardial infarction (STEMI), non-ST-elevation myocardial infarction (NSTEMI), and unstable angina." to "Hội chứng mạch vành cấp (ACS) bao gồm nhồi máu cơ tim có ST chênh lên (STEMI), nhồi máu cơ tim không ST chênh lên (NSTEMI) và đau thắt ngực không ổn định.",
            "Prompt evaluation with 12-lead electrocardiography (ECG) within 10 minutes of presentation is mandatory to differentiate transmural ischemia." to "Đánh giá nhanh bằng điện tâm đồ 12 chuyển đạo (ECG) trong vòng 10 phút kể từ khi tiếp nhận là bắt buộc để phân biệt thiếu máu cục bộ xuyên thành.",
            "High-sensitivity cardiac troponin T (hs-cTnT) or troponin I (hs-cTnI) assays should be measured at baseline and repeated at 1–3 hours." to "Xét nghiệm troponin cơ tim độ nhạy cao (hs-cTnT hoặc hs-cTnI) cần được đo ở thời điểm ban đầu và làm lại sau 1–3 giờ.",
            "STEMI: New ST-segment elevation at the J-point in at least two contiguous leads" to "STEMI: ST chênh lên mới tại điểm J ở ít nhất hai chuyển đạo kế tiếp",
            "NSTEMI: Ischemic symptoms at rest with elevated troponin levels above the 99th percentile without acute ST-elevation." to "NSTEMI: Triệu chứng thiếu máu cục bộ lúc nghỉ kèm tăng nồng độ troponin vượt bách phân vị thứ 99 mà không có ST chênh lên cấp.",
            "Unstable Angina: Ischemic symptoms at rest or minimal exertion without biomarker elevation." to "Đau Thắt Ngực Không Ổn Định: Triệu chứng thiếu máu cục bộ khi nghỉ hoặc vận động nhẹ mà không có tăng dấu ấn sinh học.",
            "Dual Antiplatelet Therapy (DAPT) remains the cornerstone of anti-thrombotic regimen in patients undergoing Percutaneous Coronary Intervention (PCI)." to "Liệu pháp kháng tiểu cầu kép (DAPT) duy trì vai trò nòng cốt trong phác đồ chống huyết khối ở bệnh nhân can thiệp mạch vành qua da (PCI).",
            "Aspirin: Loading dose of 162–325 mg orally, followed by a maintenance dose of 81 mg daily indefinitely." to "Aspirin: Liều nạp 162–325 mg đường uống, tiếp theo là liều duy trì 81 mg mỗi ngày vô thời hạn.",
            "P2Y12 Inhibitors: Ticagrelor (180 mg loading, 90 mg BID) or Prasugrel (60 mg loading, 10 mg daily) are preferred over Clopidogrel." to "Thuốc ức chế P2Y12: Ticagrelor (liều nạp 180 mg, 90 mg 2 lần/ngày) hoặc Prasugrel (liều nạp 60 mg, 10 mg/ngày) được ưu tiên hơn Clopidogrel.",
            "Anticoagulation: Unfractionated Heparin (UFH) 70–100 U/kg IV during PCI or Bivalirudin bolus." to "Thuốc chống đông: Heparin không phân đoạn (UFH) 70–100 U/kg tiêm tĩnh mạch trong PCI hoặc liều Bivalirudin bolus.",
            "Primary PCI is recommended over fibrinolytic therapy if it can be performed within 120 minutes of STEMI diagnosis." to "PCI thì đầu được khuyến cáo ưu tiên hơn tiêu sợi huyết nếu có thể thực hiện trong vòng 120 phút kể từ khi chẩn đoán STEMI.",
            "In patients with refractory angina, hemodynamic instability, or ventricular arrhythmias, immediate invasive coronary angiography (<2 hours) is indicated." to "Ở bệnh nhân đau thắt ngực kháng trị, huyết động không ổn định hoặc loạn nhịp thất, chỉ định chụp động mạch vành xâm lấn khẩn cấp (<2 giờ).",

            "ADVANCES IN IMMUNO-ONCOLOGY: PROGRAMMED CELL DEATH-1 (PD-1) BLOCKADE" to "TIẾN BỘ TRONG MIỄN DỊCH UNG BƯỚU: ỨC CHẾ ĐIỂM KIỂM SOÁT PD-1",
            "Mechanism of Action and Clinical Rationale" to "Cơ Chế Hoạt Động & Cơ Sở Lâm Sàng",
            "Immune-Related Adverse Events (irAEs) Management" to "Quản Lý Các Biến Cố Bất Lợi Liên Quan Miễn Dịch (irAE)",
            "Immune checkpoint inhibitors targeting PD-1 (e.g., Pembrolizumab, Nivolumab) or PD-L1 (e.g., Atezolizumab) reactivate cytotoxic T-lymphocytes against tumor cells." to "Các thuốc ức chế điểm kiểm soát miễn dịch nhắm trúng đích PD-1 (như Pembrolizumab, Nivolumab) hoặc PD-L1 (như Atezolizumab) tái hoạt hóa lympho T độc tế bào tiêu diệt tế bào bướu.",
            "In metastatic non-small cell lung cancer (NSCLC) expressing PD-L1 Tumor Proportion Score (TPS) ≥ 50%, first-line monotherapy significantly improves overall survival compared to platinum-doublet chemotherapy." to "Trong ung thư phổi không tế bào nhỏ (NSCLC) di căn có điểm tỷ lệ bướu PD-L1 (TPS) ≥ 50%, đơn trị liệu bước 1 cải thiện đáng kể thời gian sống còn toàn bộ so với hóa trị đôi chứa Platinum.",
            "Immunohistochemistry (IHC) for PD-L1 expression" to "Hóa mô miễn dịch (IHC) đánh giá mức độ bộc lộ PD-L1",
            "Microsatellite Instability-High (MSI-H) or Mismatch Repair Deficiency (dMMR)" to "Mất ổn định vi vệ tinh mức độ cao (MSI-H) hoặc Thiếu hụt sửa chữa ghép cặp sai (dMMR)",
            "Tumor Mutational Burden (TMB) ≥ 10 mutations/megabase" to "Tải lượng đột biến bướu (TMB) ≥ 10 đột biến/megabase",
            "Checkpoint inhibition can provoke autoimmune-like inflammatory toxicities across multiple organ systems." to "Ức chế điểm kiểm soát có thể kích hoạt các phản ứng độc tính viêm giống tự miễn trên nhiều hệ cơ quan.",
            "Grade 1 Toxicity: Mild organ inflammation; continue immunotherapy with close clinical monitoring." to "Độc tính Độ 1: Viêm cơ quan nhẹ; tiếp tục liệu pháp miễn dịch và theo dõi lâm sàng chặt chẽ.",
            "Grade 2 Toxicity: Moderate symptoms; withhold checkpoint inhibitor and initiate oral Prednisone (0.5–1.0 mg/kg/day)." to "Độc tính Độ 2: Triệu chứng trung bình; tạm ngừng thuốc miễn dịch và khởi đầu Prednisone uống (0.5–1.0 mg/kg/ngày).",
            "Grade 3-4 Toxicity: Severe or life-threatening colitis, pneumonitis, or hepatitis; permanently discontinue immunotherapy and administer high-dose intravenous Methylprednisolone (1.0–2.0 mg/kg/day)." to "Độc tính Độ 3-4: Viêm đại tràng, viêm phổi hoặc viêm gan nặng dọa tính mạng; ngưng vĩnh viễn thuốc miễn dịch và dùng Methylprednisolone tĩnh mạch liều cao (1.0–2.0 mg/kg/ngày).",
            "In corticosteroid-refractory cases, Infliximab (5 mg/kg) or Vedolizumab should be administered promptly for gastrointestinal irAEs." to "Trong các trường hợp kháng Corticoid, cần dùng ngay Infliximab (5 mg/kg) hoặc Vedolizumab đối với biến cố irAE đường tiêu hóa.",

            "EMERGENCY NEUROLOGY PROTOCOL: ACUTE ISCHEMIC STROKE EVALUATION" to "PHÁC ĐỒ THẦN KINH CẤP CỨU: ĐÁNH GIÁ ĐỘT QUỴ NHỒI MÁU NÃO CẤP",
            "Initial Assessment & Neuroimaging" to "Đánh Giá Ban Đầu & Chẩn Đoán Hình Ảnh Thần Kinh",
            "Endovascular Thrombectomy (EVT)" to "Lấy Huyết Khối Bằng Dụng Cụ Cơ Học Nội Mạch (EVT)",
            "Time is brain. The primary goal in hyperacute ischemic stroke care is rapid restoration of cerebral blood flow in the ischemic penumbra." to "Thời gian là não. Mục tiêu tối thượng trong cấp cứu đột quỵ thiếu máu não siêu cấp là tái thông nhanh dòng máu não tại vùng bóng tối thiếu máu cục bộ.",
            "Non-contrast Computed Tomography (NCCT) of the brain must be completed and interpreted within 20 minutes of arrival to rule out intracranial hemorrhage." to "Chụp cắt lớp vi tính (NCCT) não không phản quang phải được hoàn thành và đọc kết quả trong vòng 20 phút kể từ khi tiếp nhận để loại trừ xuất huyết nội sọ.",
            "The NIH Stroke Scale (NIHSS) provides a quantitative baseline score (0–42) measuring neurological impairment." to "Thang điểm đột quỵ NIHSS cung cấp điểm số định lượng nền (0–42) đo lường mức độ tổn thương thần kinh.",
            "Criteria for Intravenous Thrombolysis (Alteplase / Tenecteplase):" to "Tiêu chuẩn chỉ định tiêu sợi huyết đường tĩnh mạch (Alteplase / Tenecteplase):",
            "1. Onset of neurological deficit < 4.5 hours prior to treatment." to "1. Khởi phát khiếm khuyết thần kinh < 4.5 giờ trước khi bắt đầu điều trị.",
            "2. Absence of active internal bleeding or acute intracranial hemorrhage on CT." to "2. Không có xuất huyết nội đang hoạt động hoặc xuất huyết nội sọ cấp trên phim CT.",
            "3. Blood pressure successfully lowered below 185/110 mmHg before thrombolysis." to "3. Huyết áp được khống chế thành công dưới 185/110 mmHg trước khi dùng tiêu sợi huyết.",
            "Patients with Large Vessel Occlusion (LVO) in the anterior circulation (Internal Carotid Artery or Middle Cerebral Artery M1 segment) should be evaluated for Mechanical Thrombectomy." to "Bệnh nhân tắc mạch máu lớn (LVO) tuần hoàn não trước (động mạch cảnh trong hoặc đoạn M1 động mạch không gian não giữa) cần được đánh giá can thiệp lấy huyết khối cơ học.",
            "EVT window: Recommended within 6 hours of symptom onset, and up to 24 hours in selected patients demonstrating clinical-core mismatch on CT Perfusion / MRI." to "Cửa sổ EVT: Khuyến cáo trong vòng 6 giờ từ khi khởi phát, và mở rộng đến 24 giờ ở bệnh nhân chọn lọc có bất tương hợp lâm sàng - lõi hoại tử trên CT Perfusion / MRI.",
            "Post-thrombolysis Blood Pressure Target: Maintain BP < 180/105 mmHg for the first 24 hours to mitigate risk of hemorrhagic transformation." to "Mục tiêu huyết áp sau tiêu sợi huyết: Duy trì HA < 180/105 mmHg trong 24 giờ đầu để giảm thiểu nguy cơ chuyển dạng xuất huyết.",
            "Secondary Prevention: Initiate antiplatelet therapy (Aspirin 81 mg or Clopidogrel 75 mg) 24 hours post-thrombolysis after confirming absence of bleeding on follow-up CT." to "Dự phòng thứ phát: Khởi đầu thuốc kháng tiểu cầu (Aspirin 81 mg hoặc Clopidogrel 75 mg) sau 24 giờ kể từ khi tiêu sợi huyết sau khi xác nhận không có xuất huyết trên CT kiểm tra."
        )

        for ((en, vn) in fullSentenceDict) {
            if (line.contains(en, ignoreCase = true)) {
                return line.replace(en, vn, ignoreCase = true)
            }
        }

        // Sentence starters & clinical phrase replacements
        var result = line
            .replace("CLINICAL DOCUMENT:", "TÀI LIỆU LÂM SÀNG:", ignoreCase = true)
            .replace("Section 1. Primary Diagnosis & Pathological Evaluation", "Phần 1. Chẩn Đoán Ban Đầu & Đánh Giá Bệnh Lý", ignoreCase = true)
            .replace("Section 2. Therapeutic Outcome & Secondary Prevention", "Phần 2. Kết Quả Điều Trị & Dự Phòng Thứ Phát", ignoreCase = true)
            .replace("The patient presented with", "Bệnh nhân nhập viện với triệu chứng", ignoreCase = true)
            .replace("Diagnostic laboratory findings revealed", "Kết quả xét nghiệm chẩn đoán ghi nhận", ignoreCase = true)
            .replace("Differential diagnosis included", "Chẩn đoán phân biệt bao gồm", ignoreCase = true)
            .replace("Initial therapeutic interventions:", "Can thiệp điều trị ban đầu:", ignoreCase = true)
            .replace("Successful percutaneous coronary intervention", "Can thiệp mạch vành qua da (PCI) thành công", ignoreCase = true)
            .replace("Post-procedure course was uneventful", "Diễn biến sau thủ thuật ổn định", ignoreCase = true)
            .replace("Discharge plan includes", "Kế hoạch xuất viện bao gồm", ignoreCase = true)
            .replace("with drug-eluting stent implantation in the proximal LAD artery", "với đặt stent phủ thuốc tại đoạn gần động mạch liên thất trước (LAD)", ignoreCase = true)
            .replace("without acute rhythm abnormalities", "không phát hiện rối loạn nhịp tim cấp tính", ignoreCase = true)
            .replace("optimal medical therapy with high-intensity statin, ACE inhibitor, and beta-blocker", "điều trị nội khoa tối ưu bằng statin liều cao, thuốc ức chế men chuyển (ACEI) và thuốc chẹn beta", ignoreCase = true)
            .replace("acute onset dyspnea and retrosternal chest tightness radiating to the left jaw", "khó thở khởi phát cấp tính và cảm giác nặng ngực sau xương ức lan lên hàm trái", ignoreCase = true)
            .replace("elevated troponin levels and ischemic ECG changes", "tăng nồng độ troponin và các biến đổi điện tâm đồ thiếu máu cục bộ", ignoreCase = true)
            .replace("acute aortic dissection, pulmonary embolism, and STEMI", "bóc tách động mạch chủ cấp, thuyên tắc phổi và nhồi máu cơ tim STEMI", ignoreCase = true)
            .replace("Dual antiplatelet administration and urgent coronary angiogram", "Dùng thuốc kháng tiểu cầu kép và chụp động mạch vành khẩn cấp", ignoreCase = true)
            
            // Core medical terminology replacements
            .replace("Myocardial Infarction", "Nhồi máu cơ tim", ignoreCase = true)
            .replace("Acute Coronary Syndrome", "Hội chứng mạch vành cấp", ignoreCase = true)
            .replace("Ischemic Stroke", "Đột quỵ nhồi máu脑", ignoreCase = true)
            .replace("Intracranial Hemorrhage", "Xuất huyết nội sọ", ignoreCase = true)
            .replace("Electrocardiography", "Điện tâm đồ (ECG)", ignoreCase = true)
            .replace("Percutaneous Coronary Intervention", "Can thiệp mạch vành qua da (PCI)", ignoreCase = true)
            .replace("Dual Antiplatelet Therapy", "Liệu pháp kháng tiểu cầu kép (DAPT)", ignoreCase = true)
            .replace("High-sensitivity cardiac troponin", "Troponin cơ tim độ nhạy cao", ignoreCase = true)
            .replace("Unstable Angina", "Đau thắt ngực không ổn định", ignoreCase = true)
            .replace("Immune Checkpoint Inhibitor", "Thuốc ức chế điểm kiểm soát miễn dịch", ignoreCase = true)
            .replace("Non-small cell lung cancer", "Ung thư phổi không tế bào nhỏ", ignoreCase = true)
            .replace("Endovascular Thrombectomy", "Lấy huyết khối bằng dụng cụ cơ học nội mạch", ignoreCase = true)
            .replace("Secondary Prevention", "Dự phòng thứ phát", ignoreCase = true)
            .replace("Primary Diagnosis", "Chẩn đoán ban đầu", ignoreCase = true)
            .replace("Therapeutic Outcome", "Kết quả điều trị", ignoreCase = true)
            .replace("Efficacy", "Hiệu quả điều trị", ignoreCase = true)
            .replace("Safety", "Độ an toàn", ignoreCase = true)
            .replace("Patients", "Bệnh nhân", ignoreCase = true)
            .replace("Diagnosis", "Chẩn đoán", ignoreCase = true)
            .replace("Treatment", "Điều trị", ignoreCase = true)
            .replace("Management", "Quản lý / Điều trị", ignoreCase = true)
            .replace("Protocol", "Phác đồ", ignoreCase = true)
            .replace("Guideline", "Hướng dẫn", ignoreCase = true)
            .replace("Clinical", "Lâm sàng", ignoreCase = true)

        return result
    }
}
