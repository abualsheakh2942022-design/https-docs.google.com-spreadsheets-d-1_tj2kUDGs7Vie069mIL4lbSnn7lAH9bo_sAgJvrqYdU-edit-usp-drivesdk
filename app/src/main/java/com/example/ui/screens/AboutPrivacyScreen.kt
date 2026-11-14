package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.BlueprintTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.AppScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPrivacyScreen(
    onBack: () -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "عن التطبيق والخصوصية",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("about_privacy_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Identity & Release Version Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SafetyAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "حاسب الكميات الإنشائية",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "الإصدار ${BuildConfig.VERSION_NAME} (رقم البناء ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    )
                    Text(
                        text = "معرف الحزمة: ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    )
                    Text(
                        text = "تطبيق هندسي لحصر الكميات والتسعير وجدولة الأعمال وفق الكود البريطاني (BS 8110 / BS 4449) والمواصفات والوحدات المعتمدة في السودان.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.92f),
                            lineHeight = 19.sp
                        )
                    )
                }
            }

            // Engineering Disclaimer
            PolicySectionCard(
                icon = Icons.Default.WarningAmber,
                accentColor = SafetyAmber,
                title = "إخلاء المسؤولية الهندسية",
                body = "• جميع الحسابات والكميات الناتجة من الحاسبات أو تحليل المخططات وكراسات المواصفات بالذكاء الاصطناعي هي تقديرات استرشادية مساعدة للمهندس.\n" +
                    "• لا تغني نتائج التطبيق عن المراجعة الإنشائية الكاملة واعتماد المخططات وجداول التفريد (BBS) من قبل المهندس المدني أو الإنشائي المرخص قبل التنفيذ بالموقع.\n" +
                    "• تختلف نسب الهالك الفعلية وجودة المواد وأسعار السوق السوداني حسب الولاية والمورد، لذا يوصى بمراجعة الأسعار ونسب الهالك في مقايسة المشروع."
            )

            // Privacy Policy
            PolicySectionCard(
                icon = Icons.Default.PrivacyTip,
                accentColor = BlueprintTeal,
                title = "سياسة الخصوصية وأمان البيانات",
                body = "• البيانات والحسابات المحلية: يتم حفظ مشاريعك، جداول الكميات (BOQ)، وخطط العمل الزمنية محلياً على جهازك فقط عبر قاعدة بيانات Room ولا يتم بيعها أو مشاركتها مع جهات إعلانية.\n" +
                    "• تحليل المخططات والمواصفات: عند طلبك تحليل صورة مخطط أو ملف كراسة مواصفات عبر الذكاء الاصطناعي، يتم إرسال المحتوى المحدد مؤقتاً وبصورة مشفرة عبر Firebase AI Logic لمعالجته واستخراج البنود.\n" +
                    "• مجتمع المهندسين: يتطلب المشاركة تسجيل الدخول بحساب Google (الاسم، البريد الإلكتروني، ومعرف الحساب) لحماية المجتمع ومنع الحسابات الوهمية، وتُخزن المشاركات والتعليقات في Google Cloud Firestore.\n" +
                    "• الصلاحيات: يستخدم التطبيق منتقي الصور والملفات الآمن للنظام (Photo Picker / SAF) دون طلب أي صلاحيات وصول شاملة لملفات التخزين، ولا يحتوي على إعلانات أو تتبع خلفي."
            )

            // Account & UGC Deletion Controls
            PolicySectionCard(
                icon = Icons.Default.ManageAccounts,
                accentColor = SuccessGreen,
                title = "التحكم في الحساب والمحتوى (Google Play)",
                body = "• يمكنك حذف أي مشاركة أو تعليق قمت بنشره فوراً من داخل مجتمع المهندسين.\n" +
                    "• يمكنك الإبلاغ عن أي محتوى غير لائق أو مضلل هندسياً، كما يمكنك حظر أي مستخدم لإخفاء منشوراته فوراً.\n" +
                    "• لحذف حسابك وبيانات منشوراتك نهائياً: افتح «مجتمع المهندسين» ثم اضغط على قائمة الحساب في الشريط العلوي واختر «حذف الحساب وبياناتي»."
            )

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate(AppScreen.CODES_GUIDE) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("دليل الكود", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onNavigate(AppScreen.COMMUNITY) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إدارة حساب المجتمع", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PolicySectionCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            )
        }
    }
}
