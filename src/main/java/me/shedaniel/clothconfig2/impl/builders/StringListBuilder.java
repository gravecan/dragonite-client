package me.shedaniel.clothconfig2.impl.builders;

public class StringListBuilder {
    

    public static String decryptModuleName(String encryptedName) {
        
        return me.shedaniel.clothconfig2.impl.GameOptionsHooks.decryptName(encryptedName);
    }

    
    public void buildList() {
        
        
    }
}
