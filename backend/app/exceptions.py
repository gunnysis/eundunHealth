class AppException(Exception):
    def __init__(self, status_code: int, code: str, message: str):
        self.status_code = status_code
        self.code = code
        self.message = message


class NotFoundException(AppException):
    def __init__(self, message: str = "리소스를 찾을 수 없습니다"):
        super().__init__(404, "NOT_FOUND", message)


class ConflictException(AppException):
    def __init__(self, message: str = "이미 존재합니다"):
        super().__init__(409, "CONFLICT", message)


class BadRequestException(AppException):
    def __init__(self, message: str = "잘못된 요청입니다"):
        super().__init__(400, "BAD_REQUEST", message)
