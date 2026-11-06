package de.robv.android.xposed;
public class XSharedPreferences { public XSharedPreferences(String pkg,String name){} public String getString(String k,String d){return d;} public int getInt(String k,int d){return d;} public boolean getBoolean(String k,boolean d){return d;} public void reload(){} public boolean hasFileChanged(){return false;} }
