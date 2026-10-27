package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExercisePrescriptionUnit
import com.gunnys.eundunhealth.domain.model.ExerciseType
import java.time.LocalDate
import kotlin.random.Random

/** 무장비·저충격·오프라인 완결성을 만족하는 버전 고정 HOME 콘텐츠 (§6.4.2 확정 18종). */
object HomeExerciseCatalog {
    private fun exercise(
        slug: String,
        name: String,
        bodyPart: String,
        reps: Int,
        instructions: List<String>,
        type: ExerciseType = ExerciseType.STRENGTH,
        unit: ExercisePrescriptionUnit = ExercisePrescriptionUnit.REPS,
    ) = Exercise(
        id = "local:home:$slug:v2.0",
        name = name,
        bodyPart = bodyPart,
        equipment = "없음",
        gifUrl = "",
        instructions = instructions,
        sets = 3,
        reps = reps,
        type = type,
        prescriptionUnit = unit,
    )

    val push = listOf(
        exercise(
            "standard-pushup",
            "정석 푸시업",
            "가슴/삼두",
            12,
            listOf(
                "엎드려 어깨너비보다 약간 넓게 손을 짚고 몸을 일직선으로 정렬합니다.",
                "팔꿈치를 45도 각도로 굽히며 가슴통이 바닥에 닿기 직전까지 내려갑니다.",
                "코어 긴장을 유지하며 손바닥으로 바닥을 밀어올려 시작 자세로 복귀합니다.",
            ),
        ),
        exercise(
            "knee-pushup",
            "니 푸시업",
            "가슴/어깨",
            15,
            listOf(
                "무릎을 바닥에 대고 발목을 교차하며 상체와 허벅지를 일직선으로 만듭니다.",
                "손을 어깨너비로 짚고 가슴이 바닥 쪽으로 천천히 내려가도록 팔꿈치를 굽힙니다.",
                "허리가 꺾이지 않도록 복부에 힘을 주며 바닥을 힘차게 밀어 올립니다.",
            ),
        ),
        exercise(
            "pike-pushup",
            "파이크 푸시업",
            "어깨/삼두",
            10,
            listOf(
                "푸시업 자세에서 엉덩이를 천장 쪽으로 높게 들어 올려 몸을 'V'자 형태로 만듭니다.",
                "정수리가 양손 사이 앞쪽 바닥을 향하도록 천천히 팔꿈치를 굽혀 내려갑니다.",
                "어깨 근육의 힘으로 바닥을 밀어 올리며 시작 자세로 돌아옵니다.",
            ),
        ),
        exercise(
            "chair-dips",
            "체어 딥스",
            "삼두/가슴하부",
            12,
            listOf(
                "튼튼한 의자나 소파 모서리를 손으로 짚고 다리를 앞으로 뻗어 앉는 자세를 취합니다.",
                "팔꿈치가 직각(90도)이 될 때까지 상체를 수직으로 곧게 내립니다.",
                "팔 뒤쪽 삼두근에 집중하며 팔꿈치를 펴 몸을 원래 위치로 밀어 올립니다.",
            ),
        ),
    )

    val pull = listOf(
        exercise(
            "superman-hold",
            "슈퍼맨 홀드",
            "등/척추기립근",
            12,
            listOf(
                "바닥에 엎드려 누운 상태에서 양팔을 머리 위로 쭉 뻗고 다리를 곧게 폅니다.",
                "등과 엉덩이에 긴장을 주며 가슴과 허벅지를 바닥에서 동시에 들어 올립니다.",
                "정점에서 2초간 버티며 척추기립근과 둔근을 수축한 뒤 천천히 내립니다.",
            ),
        ),
        exercise(
            "ytw-raise",
            "Y-T-W 레이즈",
            "등상부/후면어깨",
            10,
            listOf(
                "엎드린 자세에서 양팔을 'Y'자 모양으로 뻗어 엄지손가락을 천장으로 올립니다(10회).",
                "팔을 수평 'T'자로 벌려 견갑골을 모으며 들어 올립니다(10회).",
                "팔꿈치를 굽힌 'W'자 모양으로 당기며 날개뼈 안쪽을 쥐어짭니다(10회).",
            ),
        ),
        exercise(
            "bird-dog",
            "버드독",
            "등/둔근/코어",
            12,
            listOf(
                "양손과 무릎을 바닥에 댄 네발기기 자세에서 척추를 중립으로 유지합니다.",
                "오른팔을 앞으로, 왼다리를 뒤로 곧게 뻗어 몸통과 수평을 이룹니다.",
                "골반이 회전하지 않도록 코어를 고정한 뒤 제자리로 돌아오고 반대편을 수행합니다.",
            ),
        ),
        exercise(
            "reverse-snow-angel",
            "리버스 스노우 엔젤",
            "광배근/승모근",
            12,
            listOf(
                "이마를 바닥에 살짝 띄우고 엎드려 양손을 골반 옆에 두고 손바닥이 바닥을 보게 합니다.",
                "팔을 바닥에서 살짝 띄운 채 눈사람을 그리듯 머리 위로 큰 원을 그리며 회전합니다.",
                "견갑골의 움직임에 집중하며 천천히 골반 옆 원래 위치로 돌아옵니다.",
            ),
        ),
    )

    val legs = listOf(
        exercise(
            "bodyweight-squat",
            "에어 스쿼트",
            "대퇴사두/둔근",
            15,
            listOf(
                "발을 어깨너비로 벌리고 발끝을 살짝 바깥쪽으로 향하게 섭니다.",
                "무릎이 발끝 방향을 따라가도록 하며 엉덩이를 뒤로 빼고 허벅지가 지면과 수평이 될 때까지 앉습니다.",
                "발뒤꿈치로 지면을 누르며 일어서며 둔근을 단단히 수축합니다.",
            ),
        ),
        exercise(
            "walking-lunge",
            "제자리 런지",
            "대퇴부/둔근",
            12,
            listOf(
                "상체를 곧게 세우고 한 발을 앞으로 크게 내딛습니다.",
                "앞다리와 뒷다리의 무릎이 모두 직각(90도)이 되도록 수직으로 몸을 낮춥니다.",
                "앞발 뒤꿈치로 바닥을 차며 시작 자세로 돌아오고 다리를 번갈아 진행합니다.",
            ),
        ),
        exercise(
            "glute-bridge",
            "글루트 브릿지",
            "둔근/햄스트링",
            15,
            listOf(
                "등을 바닥에 대고 누워 무릎을 세우고 발바닥을 골반 너비로 바닥에 둡니다.",
                "엉덩이를 조이며 골반을 들어 올려 무릎부터 어깨까지 사선 일직선을 만듭니다.",
                "정점에서 1~2초간 둔근을 강하게 쥐어짠 후 천천히 허리가 닿지 않게 내립니다.",
            ),
        ),
        exercise(
            "standing-calf-raise",
            "스탠딩 카프 레이즈",
            "종아리/발목",
            20,
            listOf(
                "벽이나 의자를 가볍게 짚고 발을 골반 너비로 평행하게 둡니다.",
                "발뒤꿈치를 최대한 높게 들어 올려 종아리 근육(비복근)을 강하게 수축합니다.",
                "뒤꿈치가 바닥에 닿기 직전까지 천천히 통제하며 내려옵니다.",
            ),
        ),
    )

    val core = listOf(
        exercise(
            "forearm-plank",
            "엘보우 플랭크",
            "복횡근/전신코어",
            45,
            listOf(
                "팔꿈치를 어깨 바로 아래에 두고 엎드려 전완과 발끝으로 체중을 지탱합니다.",
                "복부와 둔근에 힘을 주어 머리, 어깨, 엉덩이, 발목이 완벽한 일직선을 유지합니다.",
                "허리가 아래로 꺾이거나 엉덩이가 솟지 않도록 호흡을 일정하게 유지하며 버팁니다.",
            ),
            unit = ExercisePrescriptionUnit.SECONDS,
        ),
        exercise(
            "deadbug",
            "데드버그",
            "복직근/코어안정",
            12,
            listOf(
                "등을 대고 누워 무릎을 90도로 세우고 양팔을 천장으로 뻗습니다.",
                "허리가 바닥에서 뜨지 않도록 배꼽을 바닥 쪽으로 누르며 오른팔과 왼다리를 바닥 쪽으로 내립니다.",
                "복부 긴장을 유지한 채 시작 위치로 당겨오고 교차하여 반복합니다.",
            ),
        ),
        exercise(
            "lying-leg-raise",
            "레그 레이즈",
            "하복부",
            12,
            listOf(
                "바닥에 누워 양손을 엉덩이 밑에 가볍게 받치고 다리를 모아 곧게 폅니다.",
                "허리가 뜨지 않는 범위 내에서 하복부의 힘으로 다리를 수직에 가깝게 들어 올립니다.",
                "다리가 바닥에 닿기 직전까지 속도를 제어하며 천천히 내립니다.",
            ),
        ),
    )

    val cardio = listOf(
        exercise(
            "mountain-climber",
            "마운틴 클라이머",
            "전신유산소/복부",
            20,
            listOf(
                "푸시업 자세를 취하고 어깨 아래에 손목이 오도록 정렬합니다.",
                "복부에 힘을 주고 한쪽 무릎을 가슴 방향으로 빠르게 끌어당깁니다.",
                "점프하듯 발을 교차하며 양다리를 리드미컬하게 번갈아 당깁니다 (발소리가 나지 않게 조절).",
            ),
            type = ExerciseType.CARDIO,
        ),
        exercise(
            "jumping-jacks",
            "점핑잭",
            "전신순환/유산소",
            30,
            listOf(
                "발을 모으고 차려 자세로 섭니다.",
                "가볍게 점프하며 발을 어깨너비로 벌리고 양손을 머리 위로 올려 박수를 칩니다.",
                "다시 가볍게 뛰어 시작 자세로 돌아옵니다 (층간소음 우려 시 뒤꿈치를 든 소프트 랜딩).",
            ),
            type = ExerciseType.CARDIO,
        ),
        exercise(
            "skater-hops",
            "스케이터 홉스",
            "하체민첩성/유산소",
            16,
            listOf(
                "스케이트를 타듯 오른쪽으로 넓게 뛰며 오른발로 착지하고 왼발을 뒤로 보냅니다.",
                "착지 시 무릎을 살짝 굽혀 충격을 흡수하고 상체를 안정적으로 유지합니다.",
                "즉시 왼쪽으로 탄력 있게 뛰며 반대쪽으로 전환하여 반복합니다.",
            ),
            type = ExerciseType.CARDIO,
        ),
    )

    val all: List<Exercise> = push + pull + legs + core + cardio
}

object HomeWeeklyPlanGenerator {
    fun generate(weekStart: LocalDate, restDays: List<Int>): List<DayPlan> {
        val random = Random(weekStart.toEpochDay())
        val push = HomeExerciseCatalog.push.shuffled(random)
        val pull = HomeExerciseCatalog.pull.shuffled(random)
        val legs = HomeExerciseCatalog.legs.shuffled(random)
        val core = HomeExerciseCatalog.core.shuffled(random)
        val cardio = HomeExerciseCatalog.cardio.shuffled(random)
        val slots = listOf(
            push.take(3) + core.take(1),
            legs,
            pull,
            cardio.take(2),
            listOf(push.last(), legs[2], core.last()),
            cardio.drop(1).take(2),
        )
        val restDayOfWeeks = normalizeRestDays(restDays)
        var slotIndex = 0
        return (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek in restDayOfWeeks) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                DayPlan(date, slots[slotIndex++], isRestDay = false, isCompleted = false)
            }
        }
    }
}
