package com.springcloud.config.client.exception;

public class ConfigException extends RuntimeException {

    private int errorCode;

    private String errMsg;
    
    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }


    public ConfigException(int errorCode, String errMsg) {
        this.errorCode = errorCode;
        this.errMsg = errMsg;
    }

    public ConfigException(String errMsg) {
        super(errMsg);
        this.errMsg = errMsg;
    }

    public ConfigException(Throwable cause) {
        super(cause);
    }

    public ConfigException(Throwable cause, String errMsg) {
        super(cause);
        this.errMsg = errMsg;
    }

}
