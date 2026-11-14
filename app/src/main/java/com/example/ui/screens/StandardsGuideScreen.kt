package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EngineeringTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberDark

@Composable
fun StandardsGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "الكود البريطاني والمواصفات السودانية",
                subtitle = "BS 8110 / BS 5328 وهيئة المواصفات السودانية (SSMO)",
                showBackButton = true,
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Concrete Cover Table (BS 8110)
            item {
                SectionHeader(
                    title = "1. الغطاء الخرساني الأدنى (Concrete Cover)",
                    icon = Icons.Default.Shield,
                    badgeText = "BS 8110"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "سماكة الغطاء الخرساني لحماية حديد التسليح من الصدأ والحريق:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        CoverRow("الأساسات والقواعد الملامسة مباشرة للتربة", "75 مم (7.5 سم)")
                        CoverRow("القواعد والأساسات مع فرشة نظافة عازلة", "50 مم (5.0 سم)")
                        CoverRow("الميد والشناجات الأرضية (Ground Beams)", "40 مم - 50 مم")
                        CoverRow("الأعمدة والكمرات الخرسانية", "25 مم - 40 مم")
                        CoverRow("البلاطات والأسقف المصمتة والفلات", "20 مم - 25 مم")
                        CoverRow("الحوائط الساندة والخزانات المائية", "40 مم - 50 مم")
                    }
                }
            }

            // 2. Hot Weather Concreting in Sudan
            item {
                SectionHeader(
                    title = "2. الصب في الطقس الحار بالسودان",
                    icon = Icons.Default.WbSunny,
                    badgeText = "حرارة > 35°م"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GuidePoint(
                            number = "أ",
                            title = "توقيت الصب المفضل",
                            description = "تجنب الصب في ذروة الظهيرة صيفاً (بين 11 صباحاً و 4 عصراً). يفضل الصب في الصباح الباكر أو بعد غروب الشمس لتفادي التبخر السريع لماء الخلط."
                        )
                        GuidePoint(
                            number = "ب",
                            title = "تبريد الركام والماء",
                            description = "رش أكوام الكنكري بالماء وتغطية الرمل بالخيش لتقليل درجة حرارة المواد الأولية، واستخدام مياه باردة أو ألواح ثلج مجروشة في خلاطات الخرسانة الجاهزة."
                        )
                        GuidePoint(
                            number = "ج",
                            title = "المعالجة بالرش والخيش (Curing)",
                            description = "تغطية الخرسانة بالخيش المبلل فور تصلدها الابتدائي ورشها بالماء النظيف مرتين إلى ثلاث مرات يومياً لمدة لا تقل عن 7 أيام متتالية (ويفضل 14 يوماً للأعمدة والأسقف)."
                        )
                        GuidePoint(
                            number = "د",
                            title = "نسبة الماء للأسمنت (W/C ratio)",
                            description = "المحافظة على نسبة م/س بين 0.45 إلى 0.50 واستخدام الملدنات (Superplasticizers) لزيادة قابلية التشغيل (Workability) بدلاً من زيادة الماء العشوائية."
                        )
                    }
                }
            }

            // 3. Formwork Stripping Times (BS 8110)
            item {
                SectionHeader(
                    title = "3. مدد فك الشدات الخشبية (Stripping Time)",
                    icon = Icons.Default.Timer,
                    badgeText = "BS 8110"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "الأزمنة الدنيا لفك القوالب الخشبية في طقس السودان الدافئ/الحار:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        CoverRow("جوانب الأعمدة، الكمرات، والميد (غير حاملة)", "24 إلى 48 ساعة")
                        CoverRow("بطنيات الأسقف التي بحرها (L) حتى 3 أمتار", "7 أيام")
                        CoverRow("بطنيات الأسقف والكمرات (بحر L > 3م)", "2L + 2 يوم (مثلاً بحر 4م = 10 أيام)")
                        CoverRow("الكوابيل والكنتليفر (Cantilever)", "4L + 2 يوم (مثلاً كابولي 1.5م = 8 أيام)")
                    }
                }
            }

            // 4. Sudanese Engineering Commercial Glossary
            item {
                SectionHeader(
                    title = "4. معجم المصطلحات السوقية في السودان",
                    icon = Icons.Default.Translate,
                    badgeText = "مصطلحات الموقع"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlossaryItem("الكنكري", "الركام الخشن (الحصى / السن المكسر) مقاس 3/4 أو 1/2 بوصة المستخدم في الخرسانة.")
                        GlossaryItem("لوري تاتة (القلاب الكبير)", "سيارة قلاب كبيرة لنقل الرمل أو الكنكري تسع حوالي 16 إلى 18 متر مكعب.")
                        GlossaryItem("السكسويل", "سيارة قلاب متوسطة بست عجلات سعتها حوالي 10 إلى 12 متر مكعب.")
                        GlossaryItem("القلاب الصغير (نص لوري)", "قلاب نقل محلي يسع حوالي 4 إلى 5 متر مكعب من الرمل أو الكنكري.")
                        GlossaryItem("الشوال", "كيس الأسمنت القياسي زنة 50 كجم (20 شوال = 1 طن).")
                        GlossaryItem("البرميل", "برميل الماء المعدني أو البلاستيكي سعة 200 لتر المستخدم للخلط والرش في الموقع.")
                        GlossaryItem("الدكة", "الفرشة الخرسانية الأرضية (Slab on Grade) للأفنية والغرف قبل البلاط.")
                        GlossaryItem("الكانة الصندوق / الأوتوماتيك", "كانات حديد 8 مم مقفلة بأقفال زاوية 135 درجة لحبس الحديد الطولي للأعمدة والكمرات.")
                        GlossaryItem("سلك الرباط", "سلك أسود مجلفن ناعم لتربيط أسياخ التسليح (يستهلك ~ 6-8 كجم لكل طن حديد).")
                    }
                }
            }
        }
    }
}

@Composable
fun CoverRow(element: String, cover: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = element,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = cover,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun GuidePoint(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
fun GlossaryItem(term: String, meaning: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(SafetyAmber, shape = RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = term,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            ),
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
    }
}
