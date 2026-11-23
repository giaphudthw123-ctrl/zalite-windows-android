package org.ctdozlite.mobile;

/** Metadata only: selecting a GIF must not retain decoded animations for every conversation. */
final class GifChoice {
    final String id,name;
    final int bytes,width,height;
    GifChoice(String id,String name,int bytes,long dimensions){this.id=id;this.name=name;this.bytes=bytes;width=(int)(dimensions>>>32);height=(int)dimensions;}
}
