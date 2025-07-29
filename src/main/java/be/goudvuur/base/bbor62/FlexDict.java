/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *  A little map helper class with a static and dynamic part so we don't have to copy in the static entries every time.
 *
 * Created by bram on Nov 29, 2024
 */
public class FlexDict
{
    //-----CONSTANTS-----

    //-----VARIABLES-----
    final Map<Object, Object> staticDict;
    final Map<Object, Object> dynamicDict;
    private final boolean enableDynamic;
    private final int maxSize;

    //-----CONSTRUCTORS-----
    public FlexDict(Map<Object, Object> staticDict)
    {
        this(staticDict, true, Integer.MAX_VALUE);
    }
    public FlexDict(Map<Object, Object> staticDict, boolean enableDynamic, int maxSize)
    {
        // splitting them means we don't have to copy in the entire static dict every time
        this.staticDict = staticDict;
        // note that eg. our bbor field compression requires this to be an ordered map!
        this.dynamicDict = new LinkedHashMap<>();
        this.enableDynamic = enableDynamic;
        this.maxSize = maxSize;
    }

    //-----PUBLIC METHODS-----
    public Object get(Object key)
    {
        return this.staticDict.containsKey(key) ? this.staticDict.get(key) : this.dynamicDict.get(key);
    }
    public boolean hasKey(Object key)
    {
        return this.staticDict.containsKey(key) || this.dynamicDict.containsKey(key);
    }
    public void add(Object key, Object val)
    {
        // the caller is responsible for calling reset()
        if (this.enableDynamic && this.size() < this.maxSize && !this.hasKey(key)) {
            //if (ENABLE_DEBUG) Logger.log("PUT\t|" + key + "|\t\t|" + val + "|");
            this.dynamicDict.put(key, val);
        }
    }
    public int size()
    {
        return this.staticDict.size() + this.dynamicDict.size();
    }
    public void reset()
    {
        this.dynamicDict.clear();
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
