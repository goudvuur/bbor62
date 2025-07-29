/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.test;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by bram on Dec 06, 2024
 */
public class TestPojo
{
    //-----CONSTANTS-----
    public static class Org {

        private String value;

        public Org()
        {
        }

        @JsonProperty("name")
        public String getValue()
        {
            return value;
        }
        public void setValue(String name)
        {
            this.value = name;
        }
    }

    //-----VARIABLES-----
    private Org value;

    //-----CONSTRUCTORS-----
    public TestPojo()
    {
    }

    //-----PUBLIC METHODS-----
    @JsonProperty("org")
    public Org getValue()
    {
        return value;
    }
    public void setValue(Org org)
    {
        this.value = org;
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
