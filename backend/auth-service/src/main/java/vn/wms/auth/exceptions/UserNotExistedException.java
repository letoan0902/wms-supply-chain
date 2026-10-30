package vn.wms.auth.exceptions;

import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

public class UserNotExistedException extends ApiException {
    public UserNotExistedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
