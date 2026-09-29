package ac.sfj.afl3.exception

/**
 * Dilempar layer service ketika data yang diminta tidak ada.
 * Dipetakan ke HTTP 404 oleh [GlobalExceptionHandler].
 */
class ResourceNotFoundException(resource: String, id: Any) :
    RuntimeException("$resource dengan id $id tidak ditemukan")
