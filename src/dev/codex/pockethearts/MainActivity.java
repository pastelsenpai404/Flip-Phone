package dev.codex.pockethearts;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public final class MainActivity extends Activity {
    private GameView game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        game = new GameView(this);
        setContentView(game);
    }

    @Override protected void onPause() {
        super.onPause();
        if (game != null) game.pause();
    }

    @Override public void onBackPressed() {
        if (game.mode == GameView.PLAYING) game.pause();
        else super.onBackPressed();
    }

    private static final class Drop {
        float x, y, speed, size;
        boolean gold;
        Drop(float x, float speed, boolean gold) {
            this.x = x; this.y = -30; this.speed = speed;
            this.gold = gold; this.size = gold ? 17 : 14;
        }
    }

    private static final class Spark {
        float x, y, vx, vy, life;
        int color;
        Spark(float x, float y, float vx, float vy, int color) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.life=0.65f; this.color=color;
        }
    }

    private static final class GameView extends View {
        static final int TITLE=0, PLAYING=1, PAUSED=2, OVER=3;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final ArrayList<Drop> drops = new ArrayList<Drop>();
        private final ArrayList<Spark> sparks = new ArrayList<Spark>();
        private final Path heart = new Path();
        private final int cream=Color.rgb(255,247,238);
        private final int pink=Color.rgb(255,112,145);
        private final int dark=Color.rgb(72,48,73);
        private final int gold=Color.rgb(255,192,84);
        int mode=TITLE, score=0, best=0, lives=3, streak=0;
        float player=180, spawn=0, age=0;
        boolean left=false, right=false;
        long lastFrame=0;

        GameView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            best=context.getSharedPreferences("score",0).getInt("best",0);
            requestFocus();
        }

        void start() {
            mode=PLAYING; score=0; lives=3; streak=0; age=0; spawn=0;
            player=180; left=false; right=false;
            drops.clear(); sparks.clear(); lastFrame=0;
            invalidate();
        }

        void pause() {
            if (mode==PLAYING) { mode=PAUSED; left=false; right=false; invalidate(); }
        }

        @Override protected void onDraw(Canvas actual) {
            super.onDraw(actual);
            float sx=getWidth()/360f, sy=getHeight()/640f;
            actual.save(); actual.scale(sx,sy);
            long now=System.nanoTime();
            float dt=lastFrame==0 ? 0 : Math.min(0.05f,(now-lastFrame)/1000000000f);
            lastFrame=now;
            if (mode==PLAYING) update(dt);
            drawScene(actual);
            actual.restore();
            postInvalidateDelayed(16);
        }

        private void update(float dt) {
            age+=dt;
            if (left) player-=230*dt;
            if (right) player+=230*dt;
            player=Math.max(36,Math.min(324,player));
            spawn-=dt;
            if (spawn<=0) {
                boolean special=random.nextInt(8)==0;
                drops.add(new Drop(28+random.nextInt(304),125+Math.min(155,age*4)+random.nextInt(45),special));
                spawn=Math.max(.38f,.82f-age*.006f);
            }
            for (Iterator<Drop> it=drops.iterator();it.hasNext();) {
                Drop d=it.next(); d.y+=d.speed*dt;
                if (d.y>516 && d.y<557 && Math.abs(d.x-player)<35) {
                    int points=d.gold?3:1; score+=points; streak++;
                    if (score>best) {
                        best=score;
                        getContext().getSharedPreferences("score",0).edit().putInt("best",best).apply();
                    }
                    for(int i=0;i<8;i++) {
                        float a=(float)(i*Math.PI/4);
                        sparks.add(new Spark(d.x,d.y,(float)Math.cos(a)*75,(float)Math.sin(a)*75,d.gold?gold:pink));
                    }
                    it.remove();
                } else if (d.y>580) {
                    it.remove(); lives--; streak=0;
                    if (lives<=0) { mode=OVER; left=false; right=false; }
                }
            }
            for (Iterator<Spark> it=sparks.iterator();it.hasNext();) {
                Spark s=it.next(); s.x+=s.vx*dt; s.y+=s.vy*dt;
                s.vy+=80*dt; s.life-=dt;
                if(s.life<=0) it.remove();
            }
        }

        private void drawScene(Canvas c) {
            c.drawColor(cream);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255,226,222));
            c.drawCircle(-27,75,98,p);
            p.setColor(Color.rgb(255,235,223));
            c.drawCircle(390,405,105,p);
            p.setColor(Color.rgb(255,250,245));
            c.drawRoundRect(16,16,344,622,25,25,p);
            p.setColor(Color.rgb(255,228,227));
            c.drawRoundRect(16,16,344,99,25,25,p);
            c.drawRect(16,69,344,99,p);
            text(c,"POCKET HEARTS",180,53,22,dark,true);
            text(c,"SCORE  "+score,79,86,15,dark,true);
            text(c,"BEST  "+best,280,86,15,dark,true);
            p.setColor(Color.rgb(255,226,222));
            c.drawRoundRect(28,554,332,563,5,5,p);
            for (int i=0;i<lives;i++) heart(c,282+i*19,120,9,pink);
            text(c,"LIVES",243,125,12,dark,false);
            for (Drop d:drops) heart(c,d.x,d.y,d.size,d.gold?gold:pink);
            for(Spark s:sparks) {
                p.setColor(s.color); p.setAlpha((int)(255*Math.min(1,s.life/.65f)));
                c.drawCircle(s.x,s.y,3,p); p.setAlpha(255);
            }
            p.setColor(dark);
            c.drawRoundRect(player-32,532,player+32,546,7,7,p);
            p.setColor(pink);
            c.drawRoundRect(player-25,516,player+25,540,11,11,p);
            heart(c,player,526,9,Color.WHITE);
            text(c,"4 / LEFT       5 / OK       6 / RIGHT",180,596,13,dark,true);
            if(mode!=PLAYING) overlay(c);
        }

        private void overlay(Canvas c) {
            p.setColor(Color.argb(220,255,247,238));
            c.drawRoundRect(27,151,333,471,24,24,p);
            p.setColor(Color.rgb(255,220,220));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            c.drawRoundRect(27,151,333,471,24,24,p);
            p.setStyle(Paint.Style.FILL);
            heart(c,180,226,42,pink);
            if(mode==TITLE) {
                text(c,"CATCH THE HEARTS",180,318,23,dark,true);
                text(c,"Move left and right to catch",180,353,16,dark,true);
                text(c,"every falling heart.",180,377,16,dark,true);
                text(c,"Press 5 / OK to play",180,425,17,pink,true);
            } else if(mode==PAUSED) {
                text(c,"PAUSED",180,323,28,dark,true);
                text(c,"Press 5 / OK to continue",180,379,17,pink,true);
            } else {
                text(c,"GAME OVER",180,320,27,dark,true);
                text(c,"Score "+score+"   /   Best "+best,180,361,18,dark,true);
                text(c,"Press 5 / OK to retry",180,411,17,pink,true);
            }
        }

        private void heart(Canvas c,float x,float y,float s,int color) {
            heart.reset();
            heart.moveTo(x,y+s*.85f);
            heart.cubicTo(x-s*1.55f,y-s*.05f,x-s*.9f,y-s*1.2f,x,y-s*.45f);
            heart.cubicTo(x+s*.9f,y-s*1.2f,x+s*1.55f,y-s*.05f,x,y+s*.85f);
            heart.close(); p.setColor(color); c.drawPath(heart,p);
        }

        private void text(Canvas c,String value,float x,float y,float size,int color,boolean center) {
            p.setColor(color); p.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            p.setTextSize(size); p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);
            c.drawText(value,x,y,p);
        }

        @Override public boolean onKeyDown(int code,KeyEvent e) {
            if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_4) {left=true;return true;}
            if(code==KeyEvent.KEYCODE_DPAD_RIGHT || code==KeyEvent.KEYCODE_6) {right=true;return true;}
            if(code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_5) {
                if(e.getRepeatCount()==0) {
                    if(mode==TITLE || mode==OVER) start();
                    else if(mode==PAUSED) {mode=PLAYING;lastFrame=0;}
                    else pause();
                }
                return true;
            }
            return super.onKeyDown(code,e);
        }

        @Override public boolean onKeyUp(int code,KeyEvent e) {
            if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_4) {left=false;return true;}
            if(code==KeyEvent.KEYCODE_DPAD_RIGHT || code==KeyEvent.KEYCODE_6) {right=false;return true;}
            return super.onKeyUp(code,e);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                if(mode!=PLAYING) { if(mode==PAUSED) {mode=PLAYING;lastFrame=0;} else start(); }
                else {left=e.getX()<getWidth()/2f;right=!left;}
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                left=false; right=false; return true;
            }
            return true;
        }
    }
}
