package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.local.PageBlockEntity
import java.io.File
import java.io.FileOutputStream

data class ExtractedPage(
    val pageIndex: Int,
    val blocks: List<String>
)

data class SampleMedicalDoc(
    val id: String,
    val title: String,
    val category: String,
    val pages: List<List<String>> // Page index -> list of blocks
)

object PdfExtractor {

    val SAMPLE_DOCUMENTS = listOf(
        SampleMedicalDoc(
            id = "sample_cardio",
            title = "Clinical Practice Guidelines for Acute Coronary Syndrome",
            category = "Cardiology",
            pages = listOf(
                listOf(
                    "# CLINICAL PRACTICE GUIDELINE: MANAGEMENT OF ACUTE CORONARY SYNDROME",
                    "## 1. Executive Summary & Diagnostic Criteria",
                    "Acute Coronary Syndrome (ACS) encompasses ST-elevation myocardial infarction (STEMI), non-ST-elevation myocardial infarction (NSTEMI), and unstable angina.",
                    "Prompt evaluation with 12-lead electrocardiography (ECG) within 10 minutes of presentation is mandatory to differentiate transmural ischemia.",
                    "High-sensitivity cardiac troponin T (hs-cTnT) or troponin I (hs-cTnI) assays should be measured at baseline and repeated at 1–3 hours.",
                    "- STEMI: New ST-segment elevation at the J-point in at least two contiguous leads (≥2.5 mm in men <40 years, ≥2.0 mm in men ≥40 years, or ≥1.5 mm in women in leads V2–V3).",
                    "- NSTEMI: Ischemic symptoms at rest with elevated troponin levels above the 99th percentile without acute ST-elevation.",
                    "- Unstable Angina: Ischemic symptoms at rest or minimal exertion without biomarker elevation."
                ),
                listOf(
                    "## 2. Pharmacological & Invasive Management",
                    "Dual Antiplatelet Therapy (DAPT) remains the cornerstone of anti-thrombotic regimen in patients undergoing Percutaneous Coronary Intervention (PCI).",
                    "1. Aspirin: Loading dose of 162–325 mg orally, followed by a maintenance dose of 81 mg daily indefinitely.",
                    "2. P2Y12 Inhibitors: Ticagrelor (180 mg loading, 90 mg BID) or Prasugrel (60 mg loading, 10 mg daily) are preferred over Clopidogrel.",
                    "3. Anticoagulation: Unfractionated Heparin (UFH) 70–100 U/kg IV during PCI or Bivalirudin bolus.",
                    "Primary PCI is recommended over fibrinolytic therapy if it can be performed within 120 minutes of STEMI diagnosis.",
                    "In patients with refractory angina, hemodynamic instability, or ventricular arrhythmias, immediate invasive coronary angiography (<2 hours) is indicated."
                )
            )
        ),
        SampleMedicalDoc(
            id = "sample_oncology",
            title = "Targeted Immunotherapy & Monoclonal Antibodies in Solid Tumor Oncology",
            category = "Oncology & Pharmacology",
            pages = listOf(
                listOf(
                    "# ADVANCES IN IMMUNO-ONCOLOGY: PROGRAMMED CELL DEATH-1 (PD-1) BLOCKADE",
                    "## 1. Mechanism of Action and Clinical Rationale",
                    "Immune checkpoint inhibitors targeting PD-1 (e.g., Pembrolizumab, Nivolumab) or PD-L1 (e.g., Atezolizumab) reactivate cytotoxic T-lymphocytes against tumor cells.",
                    "In metastatic non-small cell lung cancer (NSCLC) expressing PD-L1 Tumor Proportion Score (TPS) ≥ 50%, first-line monotherapy significantly improves overall survival compared to platinum-doublet chemotherapy.",
                    "Key biomarker evaluations prior to initiation:",
                    "* Immunohistochemistry (IHC) for PD-L1 expression",
                    "* Microsatellite Instability-High (MSI-H) or Mismatch Repair Deficiency (dMMR)",
                    "* Tumor Mutational Burden (TMB) ≥ 10 mutations/megabase"
                ),
                listOf(
                    "## 2. Immune-Related Adverse Events (irAEs) Management",
                    "Checkpoint inhibition can provoke autoimmune-like inflammatory toxicities across multiple organ systems.",
                    "Grade 1 Toxicity: Mild organ inflammation; continue immunotherapy with close clinical monitoring.",
                    "Grade 2 Toxicity: Moderate symptoms; withhold checkpoint inhibitor and initiate oral Prednisone (0.5–1.0 mg/kg/day).",
                    "Grade 3-4 Toxicity: Severe or life-threatening colitis, pneumonitis, or hepatitis; permanently discontinue immunotherapy and administer high-dose intravenous Methylprednisolone (1.0–2.0 mg/kg/day).",
                    "In corticosteroid-refractory cases, Infliximab (5 mg/kg) or Vedolizumab should be administered promptly for gastrointestinal irAEs."
                )
            )
        ),
        SampleMedicalDoc(
            id = "sample_neuro",
            title = "Emergency Protocol for Acute Ischemic Stroke & Thrombolytic Therapy",
            category = "Neurology",
            pages = listOf(
                listOf(
                    "# EMERGENCY NEUROLOGY PROTOCOL: ACUTE ISCHEMIC STROKE EVALUATION",
                    "## 1. Initial Assessment & Neuroimaging",
                    "Time is brain. The primary goal in hyperacute ischemic stroke care is rapid restoration of cerebral blood flow in the ischemic penumbra.",
                    "Non-contrast Computed Tomography (NCCT) of the brain must be completed and interpreted within 20 minutes of arrival to rule out intracranial hemorrhage.",
                    "The NIH Stroke Scale (NIHSS) provides a quantitative baseline score (0–42) measuring neurological impairment.",
                    "Criteria for Intravenous Thrombolysis (Alteplase / Tenecteplase):",
                    "1. Onset of neurological deficit < 4.5 hours prior to treatment.",
                    "2. Absence of active internal bleeding or acute intracranial hemorrhage on CT.",
                    "3. Blood pressure successfully lowered below 185/110 mmHg before thrombolysis."
                ),
                listOf(
                    "## 2. Endovascular Thrombectomy (EVT)",
                    "Patients with Large Vessel Occlusion (LVO) in the anterior circulation (Internal Carotid Artery or Middle Cerebral Artery M1 segment) should be evaluated for Mechanical Thrombectomy.",
                    "EVT window: Recommended within 6 hours of symptom onset, and up to 24 hours in selected patients demonstrating clinical-core mismatch on CT Perfusion / MRI.",
                    "Post-thrombolysis Blood Pressure Target: Maintain BP < 180/105 mmHg for the first 24 hours to mitigate risk of hemorrhagic transformation.",
                    "Secondary Prevention: Initiate antiplatelet therapy (Aspirin 81 mg or Clopidogrel 75 mg) 24 hours post-thrombolysis after confirming absence of bleeding on follow-up CT."
                )
            )
        )
    )

    fun renderPdfPageToBitmap(context: Context, pdfUri: Uri, pageIndex: Int): Bitmap? {
        return try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(pdfUri, "r")
            pfd?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                    renderer.close()
                    return null
                }
                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                bitmap
            }
        } catch (e: Exception) {
            Log.e("PdfExtractor", "Error rendering PDF page", e)
            null
        }
    }

    fun getPdfPageCount(context: Context, pdfUri: Uri): Int {
        return try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(pdfUri, "r")
            pfd?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                val count = renderer.pageCount
                renderer.close()
                count
            } ?: 1
        } catch (e: Exception) {
            1
        }
    }
}
