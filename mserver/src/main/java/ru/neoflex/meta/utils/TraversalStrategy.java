package ru.neoflex.meta.utils;

import java.util.Map;

/**
 * Created by orlov on 16.06.2015.
 */
public class TraversalStrategy {
    public static final TraversalStrategy DOWN2 = new TraversalStrategy(2, 0, Integer.MAX_VALUE);
    public static final TraversalStrategy DOWN1 = new TraversalStrategy(1, 0, Integer.MAX_VALUE);
    public static final TraversalStrategy UP1 = new TraversalStrategy(1, Integer.MAX_VALUE, 0);
    public static final TraversalStrategy DEFAULT = DOWN1 ;

    private int deep;
    private int up;
    private int down;


    public TraversalStrategy(int deep, int up, int down) {
        this.deep = deep;
        this.up = up;
        this.down = down;
    }

    public TraversalStrategy(Map<String, Object> props) {
        this();
        String deepStr = (String) props.get("__deep");
        String upStr = (String) props.get("__up");
        String downStr = (String) props.get("__down");
        if (upStr != null) {
            up = Integer.valueOf(upStr);
            if (downStr == null) {
                down = -1;
            }
        }
        if (downStr != null) {
            down = Integer.valueOf(downStr);
            if (upStr == null) {
                up = -1;
            }
        }
        if (deepStr != null) {
            deep = Integer.valueOf(deepStr);
        }
    }

    private TraversalStrategy() {
        this(TraversalStrategy.DEFAULT);
    }

    private TraversalStrategy(TraversalStrategy other) {
        this(other.deep, other.up, other.down);
    }

    private TraversalStrategy copy() {
        return new TraversalStrategy(this);
    }

    public boolean canGoToRef() { return deep > 0; }
    public boolean canGoUp() { return up > 0 || up == 0 && canGoToRef(); }
    public boolean canGoDown() { return down > 0 || down == 0 && canGoToRef(); }
    public boolean canGo() { return deep > 0 || up > 0 || down > 0; }
    public TraversalStrategy goToRef() {
        TraversalStrategy result = copy();
        result.deep = deep - 1;
        result.up = -1;
        result.down = -1;
        return result;
    }
    public TraversalStrategy goUp() {
        TraversalStrategy result = copy();
        if (up > 0){
            result.up = up - 1;
        }
        else {
            result.deep = deep - 1;
        }
        result.down = 0;
        return result;
    }

    public TraversalStrategy goDown() {
        TraversalStrategy result = copy();
        if (down > 0){
            result.down = down - 1;
        }
        else {
            result.deep = deep - 1;
        }
        //result.up = 0;
        return result;
    }
}
