/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.Command;

public class MyCommand2 implements Command {
    @Override
    public String getSubject() {
        return "irrelevant";
    }
}
