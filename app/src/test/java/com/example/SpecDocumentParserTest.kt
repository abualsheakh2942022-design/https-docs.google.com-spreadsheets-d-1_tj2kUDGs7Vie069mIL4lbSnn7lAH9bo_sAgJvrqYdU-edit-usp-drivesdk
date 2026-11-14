package com.example

import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpecDocumentParserTest {

    @Test
    fun testDocxParagraphAndTableExtraction() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:r><w:t>كراسة الشروط والمواصفات الفنية - مشروع برج النيل بالخرطوم</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:t>رتبة الخرسانة للقواعد والأعمدة C25 باستخدام أسمنت مقاوم للأملاح SRC</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>البند</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>الوحدة</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>الكمية</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>خرسانة مسلحة للقواعد</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>م³</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>45.5</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val extracted = OfficeDocumentParser.extractTextFromDocx(ByteArrayInputStream(baos.toByteArray()))
        assertTrue(extracted.contains("كراسة الشروط والمواصفات الفنية"))
        assertTrue(extracted.contains("SRC"))
        assertTrue(extracted.contains("خرسانة مسلحة للقواعد | م³ | 45.5"))
    }

    @Test
    fun testXlsxSharedStringsAndSheetExtraction() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="2" uniqueCount="2">
              <si><t>حديد تسليح قطر 16 مم</t></si>
              <si><t>طن</t></si>
            </sst>
        """.trimIndent()

        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1"><v>3.25</v></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet1Xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val extracted = OfficeDocumentParser.extractTextFromXlsx(ByteArrayInputStream(baos.toByteArray()))
        assertTrue(extracted.contains("حديد تسليح قطر 16 مم | طن | 3.25"))
    }

    @Test
    fun testArabicEncodingFallbackWindows1256() {
        val sample = "مواصفات الخرسانة والأسمنت"
        val cp1256Bytes = sample.toByteArray(Charset.forName("windows-1256"))
        val decoded = OfficeDocumentParser.decodeTextBytes(cp1256Bytes)
        assertEquals(sample, decoded)
    }

    @Test
    fun testSpecReportParsingAndWorkPlanGeneration() {
        val json = """
            {
              "projectTitle": "مبنى إداري الخرطوم 2",
              "clientOrConsultant": "مكتب النيل الهندسي",
              "recommendedMixGrade": "C25",
              "defaultRebarDiameterMm": 16,
              "concreteMixSpecsAr": "C25 للأعمدة والأسقف (400 كجم/م3)",
              "cementTypeAr": "SRC مقاوم للأملاح للأساسات وOPC للهيكل",
              "steelGradeAndDiametersAr": "BS 4449 Grade 460 أقطار 16مم و12مم و8مم",
              "concreteCoverSpecAr": "50مم للأساسات و30مم للأعمدة",
              "masonryAndMortarSpecsAr": "طوب أحمر بمونة 1:4",
              "finishingSpecsAr": "بياض 2سم بمونة 1:4 وسيراميك",
              "bs8110ComplianceNotesAr": ["مطابق لـ BS 8110 مع التوصية بالصب المسائي"],
              "extractedItems": [
                {
                  "itemCode": "1.1",
                  "category": "EXCAVATION",
                  "nameAr": "حفر أساسات",
                  "specRequirementAr": "حفر حتى منسوب التربة الصلبة",
                  "unitAr": "م³",
                  "count": 1,
                  "explicitQuantityFromDoc": 120.0
                },
                {
                  "itemCode": "2.1",
                  "category": "CONCRETE",
                  "nameAr": "خرسانة قواعد مسلحة",
                  "specRequirementAr": "C25 أسمنت SRC",
                  "unitAr": "م³",
                  "count": 10,
                  "lengthM": 2.0,
                  "widthM": 2.0,
                  "heightOrThicknessM": 0.5
                },
                {
                  "itemCode": "3.1",
                  "category": "STEEL",
                  "nameAr": "حديد تسليح رئيسي",
                  "specRequirementAr": "BS 4449 D16",
                  "unitAr": "طن",
                  "count": 1,
                  "diameterMm": 16,
                  "explicitQuantityFromDoc": 2.4
                }
              ],
              "engineeringNotesAr": ["تم تحويل طن الحديد إلى كجم بدقة"]
            }
        """.trimIndent()

        val report = SpecDocumentAiAnalyzer.parseReportJson(json, "test.docx")
        assertEquals("مبنى إداري الخرطوم 2", report.technicalSummary.projectTitle)
        assertEquals(ConcreteGrade.C25, report.recommendedMixGrade)
        assertEquals(3, report.items.size)

        val excItem = report.items[0]
        assertEquals(120.0, excItem.volumeM3, 0.01)

        val concItem = report.items[1]
        assertEquals(20.0, concItem.volumeM3, 0.01) // 10 * 2 * 2 * 0.5

        val steelItem = report.items[2]
        assertEquals(2400.0, steelItem.weightKg, 0.1) // 2.4 tonnes -> 2400 kg
        assertEquals(16, steelItem.diameterMm)

        val d16Breakdown = SteelEstimator.breakdownFromWeightKg(
            weightKg = steelItem.weightKg,
            diameter = RebarDiameter.D16,
            roleLabelAr = "من الكراسة"
        )
        val (plan, phases) = WorkPlanGenerator.generateFromSpecDocument(
            title = report.technicalSummary.projectTitle,
            location = "الخرطوم",
            pace = SchedulePace.STANDARD,
            startDateMillis = 1_700_000_000_000L,
            report = report,
            concreteGrossM3 = 21.0,
            steelTonnes = 2.4,
            steelByDiameter = listOf(d16Breakdown),
            bricksCount = 0,
            finishingAreaM2 = 0.0,
            excavationM3 = 120.0,
            waterproofingAreaM2 = 0.0
        )
        assertEquals(WorkPlanSourceType.SPEC_DOCUMENT.name, plan.sourceType)
        assertTrue(phases.size >= 4)
        assertTrue(phases.any { it.quantityHint.contains("Ø16") })
    }
}
