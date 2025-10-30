/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

public class MyCommand1 implements Command {
    @Override
    public String getSubject() {
        return "irrelevant";
    }
}
