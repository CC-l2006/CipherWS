package org.example.cipherws.common.entity;

import lombok.Data;

/**
 * 名言实体，取自 say-service 的 saying.json。
 */
@Data
public class Say {
    private int id;
    private String saying;
    private String person;
}
