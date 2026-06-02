class AppException(Exception):
    """프로젝트 전역 base exception. status_code / code / message 보유."""

    def __init__(self, status_code: int, code: str, message: str):
        self.status_code = status_code
        self.code = code
        self.message = message


class NotFoundException(AppException):
    """리소스 미존재 (404). 라우터가 HTTP 404 응답으로 자동 변환."""

    def __init__(self, message: str = "리소스를 찾을 수 없습니다"):
        super().__init__(404, "NOT_FOUND", message)


class ConflictException(AppException):
    """리소스 충돌 (409). 중복 생성 / version mismatch 등 클라이언트 충돌 시 사용."""

    def __init__(self, message: str = "이미 존재합니다"):
        super().__init__(409, "CONFLICT", message)


class BadRequestException(AppException):
    """잘못된 요청 (400). 입력 검증 실패 등 client-side error."""

    def __init__(self, message: str = "잘못된 요청입니다"):
        super().__init__(400, "BAD_REQUEST", message)
