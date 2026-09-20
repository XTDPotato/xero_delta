package com.xtdpotato.xero_delta.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Reference values shared by item behavior and the detail card. Health uses HUD points. */
public record ConsumableProfile(Kind kind, int capacity, int startupTicks, float healPerSecond,
                                int effectSeconds, int repairLevel, int repairCost,
                                double weight, String description, boolean helmet) {
    public enum Kind { HEALTH, SURGERY, WOUND, ARM_WOUND, PAIN, STAMINA, HEARING, WEIGHT, CAPACITY, RECOVERY, REPAIR }
    private static final Map<String, ConsumableProfile> PROFILES = new LinkedHashMap<>();
    static {
        health("battlefield_medical_kit",800,3.5F,30,60,1.2);
        health("outdoor_medical_kit",350,4,20,30,0.7);
        health("field_first_aid_kit",220,6,20,0,0.5);
        health("strong_injector",60,3,12,0,0.4);
        health("vehicle_first_aid_kit",90,5,6,0,0.2);
        health("simple_injector",30,3.5F,5,0,0.1);
        surgery("dek_field_surgery_kit",7,5,0.7);
        surgery("tactical_quick_release_surgery_kit",4,7,0.2);
        surgery("simple_surgery_kit",2,10,0.1);
        pain("dve_painkillers",5,3,360,0.3);
        pain("bottled_antibiotics",3,4,240,0.1);
        pain("extended_release_painkillers",1,4,200,0.1);
        put("cat_tourniquet",Kind.ARM_WOUND,4,2.5F,0,0,0,0,0.1,
            "启用后可以治疗1处手臂伤口（手臂受伤状态），最多可以使用4次；启用时间2.5秒。");
        put("elastic_bandage",Kind.WOUND,2,3,0,0,0,0,0.1,
            "启用后可以治疗1处伤口（受伤状态），最多可以使用2次；启用时间3秒。");
        repair("advanced", "combo",6,200,100,50,25,3.8,3);
        repair("precision", "kit",5,120,75,40,25,3,2.2);
        repair("standard", "kit",4,75,50,40,25,2.3,1.7);
        repair("homemade", "kit",3,50,30,25,15,1.3,1);
        boost("stamina_booster",Kind.STAMINA,300,"提升体力上限，提升体力恢复速度");
        boost("perception_booster",Kind.HEARING,300,"提升10%拾音范围");
        boost("m2_muscle_injector",Kind.WEIGHT,300,"提升负重能力");
        boost("stamina_activation_injection",Kind.STAMINA,180,"提升体力上限，提升体力恢复速度");
        boost("perception_activation_injection",Kind.HEARING,180,"提升10%拾音范围");
        boost("m1_muscle_booster",Kind.WEIGHT,180,"提升负重能力");
        boost("norepinephrine",Kind.CAPACITY,120,"可有效增加肌肉耐力，提升体力上限");
        put("oe2_combat_stimulant",Kind.RECOVERY,0,3,0,3,0,0,0.1,
            "在紧急情况下用于恢复体力的注射剂，能在3秒内回复大量体力，单次使用。");
    }
    public static ConsumableProfile get(String id) { return PROFILES.get(id); }
    public static ConsumableProfile get(ItemStack stack) {
        return get(MedicalUseRules.path(stack));
    }
    public static Map<String, ConsumableProfile> all() { return Map.copyOf(PROFILES); }
    public boolean countedUses() {
        return kind == Kind.SURGERY || kind == Kind.PAIN || kind == Kind.WOUND || kind == Kind.ARM_WOUND;
    }
    public String capacityLabel() { return kind == Kind.REPAIR ? "维修点数" : countedUses() ? "可用次数" : "耐久度"; }
    public List<String[]> effects() {
        var rows = new java.util.ArrayList<String[]>();
        rows.add(new String[]{"启用时间",number(startupTicks / 20.0F) + "秒"});
        rows.add(switch(kind) {
            case HEALTH -> new String[]{"回复生命值",number(healPerSecond) + "/秒"};
            case SURGERY -> new String[]{"治疗一个部位",""};
            case WOUND -> new String[]{"治疗一处伤口",""};
            case ARM_WOUND -> new String[]{"治疗一处手臂伤口",""};
            case PAIN -> new String[]{"止痛",effectSeconds+"秒"};
            case STAMINA -> new String[]{"提升体力属性",effectSeconds+"秒"};
            case HEARING -> new String[]{"拾音范围提升 10%",effectSeconds+"秒"};
            case WEIGHT -> new String[]{"负重提升",effectSeconds+"秒"};
            case CAPACITY -> new String[]{"体力容量增加",effectSeconds+"秒"};
            case RECOVERY -> new String[]{"体力快速回复",effectSeconds+"秒"};
            case REPAIR -> new String[]{helmet ? "恢复头盔耐久度" : "恢复护甲耐久度",""};
        });
        if (kind == Kind.HEALTH && effectSeconds > 0) rows.add(new String[]{"治疗一处伤口",""});
        if (kind == Kind.REPAIR) {
            rows.add(new String[]{"维修部位",helmet ? "头盔" : "胸甲"});
            rows.add(new String[]{"维修效率","低"});
        }
        return rows;
    }
    public static String number(float value) {
        return value == (int)value ? Integer.toString((int)value) : Float.toString(value);
    }
    private static void put(String id,Kind kind,int cap,float startup,float heal,int seconds,
                            int tier,int cost,double weight,String desc) {
        PROFILES.put(id,new ConsumableProfile(kind,cap,Math.round(startup*20),heal,seconds,tier,cost,weight,desc,id.contains("_helmet_")));
    }
    private static void health(String id,int cap,float start,float heal,int pain,double weight) {
        String desc="拥有"+cap+"耐久度；每秒可以回复"+number(heal)+"点生命值，启用时间"+number(start)+"秒。";
        if(pain>0) desc+="能消耗25点耐久治疗一处伤口，还可以消耗25耐久获得"+pain+"秒止痛效果。";
        put(id,Kind.HEALTH,cap,start,heal,pain,0,0,weight,desc);
    }
    private static void surgery(String id,int uses,float start,double weight) {
        put(id,Kind.SURGERY,uses,start,0,0,0,0,weight,"启用后可以修复1个部位上所有异常状态，最多可以使用"+uses+"次；启用时间"+number(start)+"秒。");
    }
    private static void pain(String id,int uses,float start,int seconds,double weight) {
        put(id,Kind.PAIN,uses,start,0,seconds,0,0,weight,"获得止痛效果"+seconds+"秒，能屏蔽疼痛、骨折带来的视觉影响，最多可以使用"+uses+"次；使用时间"+number(start)+"秒。");
    }
    private static void boost(String id,Kind kind,int seconds,String effect) {
        put(id,kind,0,3,0,seconds,0,0,0.1,effect+"，持续"+seconds+"秒。");
    }
    private static void repair(String prefix,String suffix,int tier,int armor,int helmet,int armorCost,int helmetCost,double armorWeight,double helmetWeight) {
        for(boolean head : new boolean[]{false,true}) {
            String part=head?"头盔":"护甲";
            int cost=head?helmetCost:armorCost;
            put(prefix+"_"+(head?"helmet":"armor")+"_repair_"+suffix,Kind.REPAIR,
                head?helmet:armor,4.5F,0,0,tier,cost,head?helmetWeight:armorWeight,
                "针对"+tier+"级装备的局内维修工具，能快速修复"+part+"耐久度。每块插板消耗工具包"+cost+"点耐久；启用时间4.5秒，完整使用后生效。");
        }
    }
}
