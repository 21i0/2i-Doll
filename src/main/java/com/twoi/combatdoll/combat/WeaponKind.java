package com.twoi.combatdoll.combat;
public enum WeaponKind {
    BOOK, STAFF, AXOLOTL;
    public static WeaponKind decode(int id) { return id==2?AXOLOTL:id==1?STAFF:BOOK; }
}
