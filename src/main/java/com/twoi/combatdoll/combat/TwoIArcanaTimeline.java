package com.twoi.combatdoll.combat;

/** Animation and late-joining viewers share milestones on the independent spell clock. */
public final class TwoIArcanaTimeline {
    public static final int DURATION = 220, IMPACT = 124, COOLDOWN = CombatWorldData.COOLDOWN, MAX_TARGETS = 64;
    public static final int CHARGE = 44, FREEZE_END = 162, REVERSE = 172;
    private static final int BOOK_SLAM_COUNT=8,BOOK_SLAM_FIRST=104,BOOK_SLAM_INTERVAL=12;
    private TwoIArcanaTimeline() { }
    public static float smooth(float from, float to, float time) {
        float x = Math.clamp((time - from) / (to - from), 0, 1);
        return x * x * (3 - 2 * x);
    }
    public static float presence(float age) {
        return age < 0 || age >= DURATION ? 0 : smooth(0, 18, age) * (1 - smooth(180, DURATION, age));
    }
    public static float domain(float age) { return presence(age) * smooth(18, 98, age); }
    public static float takeover(float age) { return presence(age) * smooth(72, 88, age); }
    public static float staffTakeover(float age) { return presence(age) * smooth(3,38,age); }
    public static float impact(float age) {
        return age < IMPACT ? 0 : (float)Math.exp(-(age - IMPACT) * .12);
    }
    public static int bookSlamCount(){return BOOK_SLAM_COUNT;}
    public static int bookSlamTick(int index){
        if(index<0||index>=BOOK_SLAM_COUNT)throw new IndexOutOfBoundsException("Book slam "+index);
        return BOOK_SLAM_FIRST+index*BOOK_SLAM_INTERVAL;
    }
    /** Latest visible slam; missed waves are never replayed as a catch-up burst. */
    public static int bookSlamAt(float age){
        return !Float.isFinite(age)||age<BOOK_SLAM_FIRST||age>=DURATION?-1:
                Math.min(BOOK_SLAM_COUNT-1,(int)((age-BOOK_SLAM_FIRST)/BOOK_SLAM_INTERVAL));
    }
    public static float bookSlamStrength(float age){
        int index=bookSlamAt(age);
        return index<0?0:(float)Math.exp(-(age-bookSlamTick(index))*.30);
    }
    /** Shared by clock, planets and nearby physical fragments; independent of world ticks. */
    public static float celestialAge(float age,float chargeExtra) {
        float reverse=Math.max(0,age-REVERSE);
        return Math.min(age,CHARGE)+chargeExtra-reverse*reverse*(4F/3F);
    }
}
