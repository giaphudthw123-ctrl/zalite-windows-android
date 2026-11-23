package org.ctdozlite.mobile;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small resolution-independent navigation glyphs; no bitmap/icon-font loading. */
final class MobileIcon extends Drawable {
    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    MobileIcon(int kind) { this.kind=kind; paint.setColor(0xff536579); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
    public void draw(Canvas canvas) {
        canvas.save(); Rect b=getBounds(); canvas.translate(b.left,b.top); canvas.scale(b.width()/24f,b.height()/24f);
        if(kind==0) { canvas.drawRoundRect(3,4,21,18,5,5,paint); Path tail=new Path(); tail.moveTo(6,18);tail.lineTo(5,22);tail.lineTo(11,18);canvas.drawPath(tail,paint);canvas.drawLine(7,9,17,9,paint);canvas.drawLine(7,13,14,13,paint); }
        else if(kind==1) { canvas.drawRoundRect(4,3,20,22,3,3,paint);canvas.drawCircle(12,9,3,paint);canvas.drawArc(7,14,17,22,180,180,false,paint);canvas.drawLine(2,7,5,7,paint);canvas.drawLine(2,12,5,12,paint);canvas.drawLine(2,17,5,17,paint); }
        else if(kind==2) { canvas.drawCircle(12,12,9,paint);Path needle=new Path();needle.moveTo(16,7);needle.lineTo(14,14);needle.lineTo(7,17);needle.lineTo(10,10);needle.close();canvas.drawPath(needle,paint); }
        else if(kind==3) { canvas.drawRoundRect(4,3,20,22,2,2,paint);canvas.drawLine(8,8,16,8,paint);canvas.drawLine(8,12,16,12,paint);canvas.drawLine(8,16,13,16,paint); }
        else if(kind==4) { canvas.drawCircle(12,7,4,paint);canvas.drawArc(3,13,21,29,180,180,false,paint); }
        else if(kind==5) { canvas.drawCircle(10,10,6,paint);canvas.drawLine(15,15,21,21,paint); }
        else if(kind==6) { canvas.drawLine(12,4,12,20,paint);canvas.drawLine(4,12,20,12,paint); }
        else if(kind==7) { canvas.drawLine(15,4,7,12,paint);canvas.drawLine(7,12,15,20,paint); }
        else if(kind==8) { canvas.drawLine(4,6,20,6,paint);canvas.drawLine(4,12,20,12,paint);canvas.drawLine(4,18,20,18,paint); }
        else if(kind==9) { canvas.drawCircle(12,12,9,paint);canvas.drawPoint(9,9,paint);canvas.drawPoint(15,9,paint);canvas.drawArc(7,10,17,18,10,160,false,paint); }
        else if(kind==10) { canvas.drawRoundRect(3,4,21,20,2,2,paint);canvas.drawCircle(8,9,2,paint);Path hill=new Path();hill.moveTo(4,18);hill.lineTo(11,12);hill.lineTo(15,16);hill.lineTo(18,13);hill.lineTo(21,16);canvas.drawPath(hill,paint); }
        else if(kind==12) { Path arrow=new Path();arrow.moveTo(4,3);arrow.lineTo(22,12);arrow.lineTo(4,21);arrow.lineTo(8,12);arrow.close();canvas.drawPath(arrow,paint);canvas.drawLine(8,12,16,12,paint); }
        else if(kind==13) { canvas.drawArc(4,4,20,20,35,290,false,paint);canvas.drawLine(20,4,20,10,paint);canvas.drawLine(14,10,20,10,paint); }
        else if(kind==14) { canvas.drawRoundRect(3,4,21,18,3,3,paint);Path bolt=new Path();bolt.moveTo(13,6);bolt.lineTo(9,12);bolt.lineTo(14,12);bolt.lineTo(11,17);canvas.drawPath(bolt,paint); }
        else { canvas.drawRoundRect(1,4,23,20,3,3,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("GIF",4,15,paint);paint.setStyle(Paint.Style.STROKE); }
        canvas.restore();
    }
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
