package com.smartatm.pretest.ui.screen

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartatm.pretest.ui.MainViewModel
import com.smartatm.pretest.ui.theme.RiskCautionBg
import com.smartatm.pretest.ui.theme.RiskCautionYellow
import com.smartatm.pretest.ui.theme.RiskDangerBg
import com.smartatm.pretest.ui.theme.RiskDangerRed
import com.smartatm.pretest.ui.theme.RiskSafeBg
import com.smartatm.pretest.ui.theme.RiskSafeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val analysisResult by viewModel.analysisResult.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    // Screen Brightness Max & Keep Screen On for ATM Camera recognition (PRD 5-4)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = originalBrightness
            }
        }
    }

    val riskLevel = analysisResult?.riskLevel ?: "SAFE"
    val riskScore = analysisResult?.riskScore ?: 0
    val reasons = analysisResult?.reasons ?: emptyList()
    val summary = analysisResult?.summary
    val atmAction = analysisResult?.atmAction ?: "ALLOW"
    val sessionId = analysisResult?.sessionId

    val (badgeBgColor, badgeTextColor, badgeIcon, badgeTitle) = when (riskLevel) {
        "DANGER" -> Quadruple(
            RiskDangerBg,
            RiskDangerRed,
            Icons.Default.Dangerous,
            "위험 (DANGER) - 출금 차단 대상"
        )
        "CAUTION" -> Quadruple(
            RiskCautionBg,
            RiskCautionYellow,
            Icons.Default.Warning,
            "주의 (CAUTION) - 본인 확인 필요"
        )
        else -> Quadruple(
            RiskSafeBg,
            RiskSafeGreen,
            Icons.Default.CheckCircle,
            "안전 (SAFE) - 정상 거래 가능"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("검사 결과", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetAnalysis()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Risk Level Badge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = badgeBgColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeTextColor,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = badgeTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "위험 점수: ${riskScore}점 / 100점 (ATM 조치: $atmAction)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                }
            }

            // QR Code Display Section (PRD 5-4: Only when CAUTION or DANGER)
            if (riskLevel != "SAFE" && qrBitmap != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "라즈베리파이 ATM 카메라에 비추세요",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "세션 ID: ${sessionId ?: "VP-XXXXXX"}",
                            fontSize = 15.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // QR Code Bitmap (High contrast, pure white background, >= 70% screen width)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .background(Color.White)
                                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "ATM 연동 QR 코드",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "※ 카메라 인식을 위해 화면 밝기가 최대로 고정됩니다.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else if (riskLevel == "SAFE") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "의심되는 보이스피싱 징후가 없습니다.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "평상시 정상 거래는 QR 코드가 발행되지 않습니다.",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            // Reasons & Summary Card
            if (reasons.isNotEmpty() || !summary.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "탐지 근거",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (!summary.isNullOrBlank()) {
                            Text(
                                text = "요약: $summary",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        reasons.forEachIndexed { index, reason ->
                            Text(
                                text = "${index + 1}. $reason",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.resetAnalysis()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("다시 검사하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
