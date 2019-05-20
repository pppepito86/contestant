package org.pesho.judge.rest;

public class ResponseMessage {

    public static ResponseMessage getOKMessage(Object data) {
        return new ResponseMessage(data, null);
    }

    public static ResponseMessage getErrorMessage(String error) {
        return new ResponseMessage(null, error);
    }

    private Object data;
    private String error;

    public ResponseMessage(Object data, String error) {
        this.data = data;
        this.error = error;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }

}
