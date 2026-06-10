package com.gunnys.eundunhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme

/**
 * Health Connect 권한 rationale(개인정보 처리방침) 화면.
 *
 * Health Connect 가 권한 grant 화면에서 '개인정보 처리방침' 진입 시 이 액티비티를 연다.
 * **중요**: Android 14+(API 34+) 통합 Health Connect 는 grant 화면을 띄우기 전
 * 요청 앱이 rationale intent(`VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS`)를 resolve
 * 할 수 있는지 검사한다. resolve 실패 시 grant 화면을 거부하고 즉시 finish 하여
 * "연동 버튼 무반응" 이 된다 (INC 2026-06-10, Android 15 실측). 따라서 이 액티비티는
 * 매니페스트의 두 intent-filter(14+ alias + ≤13 activity)가 가리키는 대상이며,
 * 존재 자체가 권한 흐름의 필수 전제다. 회귀 가드: [com.gunnys.eundunhealth.ManifestHealthConnectRationaleTest].
 *
 * 내용은 `docs/store/privacy-policy.md` §1 의 Health Connect 항목과 동기화한다.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EundunHealthTheme {
                RationaleScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun RationaleScreen(onClose: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Health Connect 데이터 사용 안내",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "은둔헬스는 아래 Health Connect 데이터를 읽기 전용으로만 사용하며, " +
                    "Health Connect 에 데이터를 쓰지(기록 추가·수정) 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            RationaleItem(
                "운동 세션",
                "주간 운동 계획의 완료 여부 판정에만 사용합니다. 원본 세션은 저장하지 않으며, " +
                    "판정된 '완료한 날짜' 만 서버에 기록됩니다.",
            )
            RationaleItem(
                "체중·체지방률",
                "프로필 '가져오기' 실행 시 입력값으로 제안되며, 사용자가 직접 '저장' 을 누른 경우에만 " +
                    "서버에 저장됩니다.",
            )
            RationaleItem(
                "걸음 수·소모 칼로리·심박수 (오늘의 활동)",
                "홈 화면 요약 표시 용도로만 사용하며, 단말을 벗어나지 않습니다(외부 서버로 전송하지 않음).",
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "허용한 권한은 언제든 Health Connect 설정에서 해제할 수 있습니다. " +
                    "전체 개인정보 처리방침은 앱 스토어 등재 페이지에서 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("확인")
            }
        }
    }
}

@Composable
private fun RationaleItem(label: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
