package com.example.data.repository

import com.example.data.local.GlossaryDao
import com.example.data.local.GlossaryEntity
import kotlinx.coroutines.flow.Flow

class GlossaryRepository(private val glossaryDao: GlossaryDao) {

    val allTerms: Flow<List<GlossaryEntity>> = glossaryDao.getAllTerms()

    fun getTermsByCategory(category: String): Flow<List<GlossaryEntity>> {
        return if (category == "Tất cả" || category == "All") {
            glossaryDao.getAllTerms()
        } else {
            glossaryDao.getTermsByCategory(category)
        }
    }

    fun searchTerms(query: String): Flow<List<GlossaryEntity>> {
        return glossaryDao.searchTerms(query)
    }

    suspend fun addCustomTerm(termEn: String, termVn: String, category: String, definitionVn: String = "") {
        glossaryDao.insertTerm(
            GlossaryEntity(
                termEn = termEn.trim(),
                termVn = termVn.trim(),
                category = category,
                definitionVn = definitionVn.trim(),
                isCustom = true
            )
        )
    }

    suspend fun deleteTerm(id: Long) {
        glossaryDao.deleteTerm(id)
    }

    suspend fun seedDefaultMedicalTermsIfEmpty() {
        if (glossaryDao.getCount() == 0) {
            glossaryDao.insertTerms(DEFAULT_MEDICAL_TERMS)
        }
    }

    companion object {
        val DEFAULT_MEDICAL_TERMS = listOf(
            GlossaryEntity(termEn = "Acute Coronary Syndrome", termVn = "Hội chứng mạch vành cấp", category = "Cardiology", definitionVn = "Bao gồm nhồi máu cơ tim có ST chênh lên (STEMI), không ST chênh lên (NSTEMI) và đau thắt ngực không ổn định."),
            GlossaryEntity(termEn = "Myocardial Infarction", termVn = "Nhồi máu cơ tim", category = "Cardiology", definitionVn = "Tình trạng hoại tử cơ tim do thiếu máu cục bộ cấp tính."),
            GlossaryEntity(termEn = "Percutaneous Coronary Intervention", termVn = "Can thiệp mạch vành qua da (PCI)", category = "Cardiology", definitionVn = "Thủ thuật đặt stent tái thông động mạch vành bị hẹp hoặc tắc."),
            GlossaryEntity(termEn = "Cardiac Troponin", termVn = "Troponin cơ tim (hs-cTn)", category = "Cardiology", definitionVn = "Protein dấu ấn sinh học đặc hiệu chẩn đoán tổn thương tế bào cơ tim."),
            GlossaryEntity(termEn = "Electrocardiography", termVn = "Điện tâm đồ (ECG / EKG)", category = "Cardiology", definitionVn = "Ghi lại hoạt động điện học của tim qua các điện cực trên da."),
            GlossaryEntity(termEn = "Ventricular Fibrillation", termVn = "Rung thất", category = "Cardiology", definitionVn = "Rối loạn nhịp thất nguy hiểm gây ngừng tuần hoàn cấp tính."),
            GlossaryEntity(termEn = "Dual Antiplatelet Therapy", termVn = "Liệu pháp kháng tiểu cầu kép (DAPT)", category = "Cardiology", definitionVn = "Phối hợp Aspirin với một thuốc ức chế thụ thể P2Y12 (Ticagrelor/Clopidogrel)."),

            GlossaryEntity(termEn = "Acute Ischemic Stroke", termVn = "Đột quỵ nhồi máu não cấp", category = "Neurology", definitionVn = "Tắc nghẽn đột ngột mạch máu cung cấp máu cho một vùng não."),
            GlossaryEntity(termEn = "Intracranial Hemorrhage", termVn = "Xuất huyết nội sọ", category = "Neurology", definitionVn = "Tình trạng chảy máu trong nhu mô não hoặc khoang dưới nhện."),
            GlossaryEntity(termEn = "Endovascular Thrombectomy", termVn = "Lấy huyết khối bằng dụng cụ cơ học (EVT)", category = "Neurology", definitionVn = "Can thiệp nội mạch can thiệp kéo huyết khối tái thông mạch máu lớn."),
            GlossaryEntity(termEn = "Ischemic Penumbra", termVn = "Vùng bóng tối mô thiếu máu não", category = "Neurology", definitionVn = "Vùng nhu mô não tổn thương có thể phục hồi nếu được tái thông kịp thời."),
            GlossaryEntity(termEn = "Alteplase", termVn = "Thuốc tiêu sợi huyết (rtPA)", category = "Neurology", definitionVn = "Enzym hoạt hóa plasminogen tái tổ hợp tiêu cục máu đông."),

            GlossaryEntity(termEn = "Immune Checkpoint Inhibitor", termVn = "Thuốc ức chế điểm kiểm soát miễn dịch", category = "Oncology", definitionVn = "Kháng thể đơn dòng tái hoạt hóa tế bào T chống lại tế bào ung thư."),
            GlossaryEntity(termEn = "Monoclonal Antibody", termVn = "Kháng thể đơn dòng (mAb)", category = "Oncology", definitionVn = "Kháng thể nhân tạo nhắm trúng đích phân tử đặc hiệu trên tế bào bướu."),
            GlossaryEntity(termEn = "Metastatic Non-Small Cell Lung Cancer", termVn = "Ung thư phổi không tế bào nhỏ di căn (NSCLC)", category = "Oncology", definitionVn = "Thể ung thư phổi ác tính phổ biến nhất ở giai đoạn di căn."),
            GlossaryEntity(termEn = "Programmed Death-Ligand 1", termVn = "Yếu tố PD-L1", category = "Oncology", definitionVn = "Protein gắn trên bề mặt tế bào ung thư để lẩn tránh hệ miễn dịch."),
            GlossaryEntity(termEn = "Immune-Related Adverse Event", termVn = "Biến cố bất lợi liên quan miễn dịch (irAE)", category = "Oncology", definitionVn = "Tác phụ tự miễn do liệu pháp miễn dịch gây ra trên các cơ quan."),

            GlossaryEntity(termEn = "Pharmacokinetics", termVn = "Dược động học (PK)", category = "Pharmacology", definitionVn = "Nghiên cứu quá trình hấp thu, phân bố, chuyển hóa và thải trừ của thuốc."),
            GlossaryEntity(termEn = "Pharmacodynamics", termVn = "Dược lực học (PD)", category = "Pharmacology", definitionVn = "Tác động sinh học và cơ chế hoạt động của thuốc lên cơ thể."),
            GlossaryEntity(termEn = "Corticosteroid", termVn = "Thuốc Corticoid chống viêm", category = "Pharmacology", definitionVn = "Nhóm thuốc hormone steroid chống viêm và chống dị ứng mạnh."),

            GlossaryEntity(termEn = "Differential Diagnosis", termVn = "Chẩn đoán phân biệt", category = "Diagnostics", definitionVn = "Quy trình loại trừ các bệnh lý có triệu chứng lâm sàng tương tự."),
            GlossaryEntity(termEn = "Computed Tomography", termVn = "Chụp cắt lớp vi tính (CT Scan)", category = "Diagnostics", definitionVn = "Kỹ thuật hiển thị hình ảnh xắt lát độ phân giải cao của cơ thể."),
            GlossaryEntity(termEn = "Magnetic Resonance Imaging", termVn = "Chụp cộng hưởng từ (MRI)", category = "Diagnostics", definitionVn = "Chẩn đoán hình ảnh dùng từ trường mạnh và sóng radio.")
        )
    }
}
