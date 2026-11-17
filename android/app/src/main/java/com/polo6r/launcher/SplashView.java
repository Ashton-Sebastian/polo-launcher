package com.polo6r.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Native VW start-up screen (dark blue, logo fades and grows in with a soft glow, then fades out).
 * Drawn without the web view so it appears instantly when the car is switched on.
 */
class SplashView extends FrameLayout {
    static final long SHOW_MS = 2300;
    static final long MAX_MS = 9000;

    interface Ready { boolean ready(); }

    private final ImageView logo;
    private final View glow;
    private final GradientDrawable bg;

    SplashView(Context c, Ready ready, Runnable onDone) {
        super(c);
        bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A2B63, 0xFF000610});
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        setBackground(bg);

        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x704FA8FF, 0x00000000});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setShape(GradientDrawable.OVAL);
        glow = new View(c);
        glow.setBackground(g);
        glow.setAlpha(0f);
        addView(glow, new LayoutParams(0, 0, Gravity.CENTER));

        logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setAlpha(0f);
        logo.setScaleX(0.9f);
        logo.setScaleY(0.9f);
        addView(logo, new LayoutParams(0, 0, Gravity.CENTER));

        setClickable(true); // swallow touches while showing
        post(() -> {
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(new DecelerateInterpolator(2f)).start();
            glow.animate().alpha(1f).setDuration(1400).start();
        });
        final long start = System.currentTimeMillis();
        // stay up until the launcher screens are actually drawn, so there is never a dark screen after the logo
        Runnable tryFinish = new Runnable() {
            @Override public void run() {
                if (ready.ready() || System.currentTimeMillis() - start > MAX_MS) {
                    animate().alpha(0f).setDuration(450).withEndAction(onDone).start();
                } else {
                    postDelayed(this, 150);
                }
            }
        };
        postDelayed(tryFinish, SHOW_MS);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bg.setGradientRadius(Math.max(w, h) * 0.75f);
        // apply child sizes after this layout pass (changes made during layout are ignored)
        post(() -> sizeChildren(w, h));
    }

    private void sizeChildren(int w, int h) {
        int d = (int) (Math.min(w, h) * 0.46f);
        LayoutParams lp = (LayoutParams) logo.getLayoutParams();
        lp.width = d; lp.height = d;
        logo.setLayoutParams(lp);
        int gd = (int) (d * 1.9f);
        LayoutParams gp = (LayoutParams) glow.getLayoutParams();
        gp.width = gd; gp.height = gd;
        glow.setLayoutParams(gp);
        ((GradientDrawable) glow.getBackground()).setGradientRadius(gd / 2f);
    }
}
