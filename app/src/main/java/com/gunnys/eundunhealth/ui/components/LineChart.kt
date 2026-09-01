package com.gunnys.eundunhealth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState

/**
 * 공유 라인 차트 — Vico 모델 프로듀서 + LaunchedEffect 채움(runBlocking 없음, 공식 패턴).
 * xLabels 가 있으면 하단 축(날짜 라벨) 부착, 없으면 생략. (Goal 진행/Statistics 완료율 공유)
 */
@Composable
fun LineChart(
    yValues: List<Double>,
    modifier: Modifier = Modifier,
    xLabels: List<String>? = null,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(yValues) {
        if (yValues.isNotEmpty()) {
            producer.runTransaction { lineModel { series(yValues) } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
                tickPosition = BaseAxis.TickPosition.Inside,
            ),
            bottomAxis = xLabels?.let { labels ->
                HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ -> labels.getOrNull(value.toInt()) ?: "" },
                )
            },
        ),
        modelProducer = producer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
