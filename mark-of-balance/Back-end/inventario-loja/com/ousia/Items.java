package com.ousia;
import java.util.*;
public final class Items {
    private static final Map<String,Item> CATALOG = new LinkedHashMap<>();
    static {
        add(new Item("fragmento","Fragmento de Equilíbrio","Material para aprimoramentos",ItemType.MATERIAL,Rarity.COMUM,12,6,0,0));
        add(new Item("essencia","Essência do Equilíbrio","Moeda oficial de Ousia",ItemType.CURRENCY,Rarity.RARO,0,0,0,0));
        add(new Item("pocao","Poção Vital","Recupera 40 pontos de vida",ItemType.CONSUMABLE,Rarity.COMUM,25,12,40,0));
        add(new Item("cristal","Cristal Ancestral","Material raro de exploração",ItemType.MATERIAL,Rarity.RARO,80,40,0,0));
        add(new Item("purificacao","Pedra de Purificação","Purifica fragmentos",ItemType.MATERIAL,Rarity.EPICO,100,50,0,0));
        add(new Item("reliquia","Relíquia Antiga","Item especial de progressão",ItemType.SPECIAL,Rarity.LENDARIO,0,0,0,0));
        add(new Item("espada","Lâmina do Conselho","Poder +5",ItemType.EQUIPMENT,Rarity.RARO,100,50,0,5));
        add(new Item("armadura","Armadura do Equilíbrio","Poder +3",ItemType.EQUIPMENT,Rarity.EPICO,140,70,0,3));
    }
    private Items(){}
    private static void add(Item i){CATALOG.put(i.getId(),i);}
    public static Item get(String id){return CATALOG.get(id);}
    public static Collection<Item> all(){return CATALOG.values();}
}
