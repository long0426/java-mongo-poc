package com.project.assets.service;

import org.springframework.dao.DataAccessException;

public class MongoRawWriteException extends DataAccessException {

    public MongoRawWriteException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
