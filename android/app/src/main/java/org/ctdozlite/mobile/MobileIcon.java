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
        else { canvas.drawCircle(12,7,4,paint);canvas.drawArc(3,13,21,29,180,180,false,paint); }
        canvas.restore();
    }
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
