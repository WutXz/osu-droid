package ru.nsu.ccfit.zuev.osu.game;

import com.reco1l.osu.graphics.Modifiers;
import com.rian.osu.mods.ModHidden;

import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.sprite.Sprite;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.RGBColor;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.scoring.ResultType;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class HitCircle extends GameObject {
    private final Sprite circle;
    private final Sprite overlay;
    private final Sprite approachCircle;
    private final RGBColor comboColor = new RGBColor();
    private com.rian.osu.beatmap.hitobject.HitCircle beatmapCircle;
    private CircleNumber number;
    private GameObjectListener listener;
    private Scene scene;
    private float radiusSquared;
    private float passedTime;
    private float timePreempt;
    private boolean kiai;

    public HitCircle() {
        // Getting sprites from sprite pool
        circle = new Sprite(0, 0, ResourceManager.getInstance().getTexture("hitcircle"));
        circle.setAlpha(0);
        overlay = new Sprite(0, 0, ResourceManager.getInstance().getTexture("hitcircleoverlay"));
        overlay.setAlpha(0);
        approachCircle = new Sprite(0, 0, ResourceManager.getInstance().getTexture("approachcircle"));
    }

    public void init(final GameObjectListener listener, final Scene pScene,
                     final com.rian.osu.beatmap.hitobject.HitCircle beatmapCircle, final float secPassed,
                     final RGBColor comboColor) {
        // Storing parameters into fields
        this.replayObjectData = null;
        this.beatmapCircle = beatmapCircle;
        this.pos = beatmapCircle.getGameplayStackedPosition().toPointF();
        this.endsCombo = beatmapCircle.isLastInCombo();
        this.listener = listener;
        this.scene = pScene;
        this.timePreempt = (float) beatmapCircle.timePreempt / 1000;
        passedTime = secPassed - ((float) beatmapCircle.startTime / 1000 - timePreempt);
        startHit = false;
        kiai = GameHelper.isKiai();
        this.comboColor.set(comboColor.r(), comboColor.g(), comboColor.b());

        // Calculating position of top/left corner for sprites and hit radius
        final float scale = beatmapCircle.getGameplayScale();
        radiusSquared = (float) beatmapCircle.getGameplayRadius();
        radiusSquared *= radiusSquared;

        float actualFadeInDuration = (float) beatmapCircle.timeFadeIn / 1000 / GameHelper.getSpeedMultiplier();
        float remainingFadeInDuration = Math.max(0, actualFadeInDuration - passedTime / GameHelper.getSpeedMultiplier());
        float fadeInProgress = 1 - remainingFadeInDuration / actualFadeInDuration;

        // Initializing sprites
        circle.setColor(comboColor.r(), comboColor.g(), comboColor.b());
        circle.setScale(scale);
        circle.setAlpha(fadeInProgress);
        Utils.putSpriteAnchorCenter(pos, circle);

        overlay.setScale(scale);
        overlay.setAlpha(fadeInProgress);
        Utils.putSpriteAnchorCenter(pos, overlay);

        approachCircle.setColor(comboColor.r(), comboColor.g(), comboColor.b());
        approachCircle.setScale(scale * (3 - 2 * fadeInProgress));
        approachCircle.setAlpha(0.9f * fadeInProgress);
        Utils.putSpriteAnchorCenter(pos, approachCircle);
        if (GameHelper.isHidden()) {
            approachCircle.setVisible(Config.isShowFirstApproachCircle() && beatmapCircle.isFirstNote());
        }

        // and getting new number from sprite pool
        int comboNum = beatmapCircle.getIndexInCurrentCombo() + 1;
        if (OsuSkin.get().isLimitComboTextLength()) {
            comboNum %= 10;
        }
        number = GameObjectPool.getInstance().getNumber(comboNum);
        number.init(pos, scale);
        number.setAlpha(0);

        if (GameHelper.isHidden()) {
            float actualFadeOutDuration = timePreempt * (float) ModHidden.FADE_OUT_DURATION_MULTIPLIER / GameHelper.getSpeedMultiplier();
            float remainingFadeOutDuration = Math.min(
                actualFadeOutDuration,
                Math.max(0, actualFadeOutDuration + remainingFadeInDuration - passedTime / GameHelper.getSpeedMultiplier())
            );
            float fadeOutProgress = remainingFadeOutDuration / actualFadeOutDuration;

            number.registerEntityModifier(Modifiers.sequence(
                    Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1),
                    Modifiers.alpha(remainingFadeOutDuration, fadeOutProgress, 0)
            ));
            overlay.registerEntityModifier(Modifiers.sequence(
                    Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1),
                    Modifiers.alpha(remainingFadeOutDuration, fadeOutProgress, 0)
            ));
            circle.registerEntityModifier(Modifiers.sequence(
                    Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1),
                    Modifiers.alpha(remainingFadeOutDuration, fadeOutProgress, 0)
            ));
        } else {
            circle.registerEntityModifier(Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1));
            overlay.registerEntityModifier(Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1));
            number.registerEntityModifier(Modifiers.alpha(remainingFadeInDuration, fadeInProgress, 1));
        }

        if (approachCircle.isVisible()) {
            approachCircle.registerEntityModifier(
                Modifiers.alpha(
                    Math.min(
                        Math.min(actualFadeInDuration * 2, remainingFadeInDuration),
                        timePreempt / GameHelper.getSpeedMultiplier()
                    ),
                    0.9f * fadeInProgress,
                    0.9f
                )
            );

            approachCircle.registerEntityModifier(
                Modifiers.scale(
            Math.max(0, timePreempt - passedTime) / GameHelper.getSpeedMultiplier(),
                    approachCircle.getScaleX(),
                    scale
                )
            );
        }

        scene.attachChild(number, 0);
        scene.attachChild(overlay, 0);
        scene.attachChild(circle, 0);
        scene.attachChild(approachCircle);
    }

    private void playSound() {
        listener.playSamples(beatmapCircle);
    }

    private void removeFromScene() {
        if (scene == null) {
            return;
        }

        overlay.clearEntityModifiers();
        circle.clearEntityModifiers();
        number.clearEntityModifiers();
        approachCircle.clearEntityModifiers();

        // Detach all objects
        overlay.detachSelf();
        circle.detachSelf();
        number.detachSelf();
        approachCircle.detachSelf();
        listener.removeObject(this);
        GameObjectPool.getInstance().putCircle(this);
        GameObjectPool.getInstance().putNumber(number);
        scene = null;
    }

    private boolean canBeHit() {
        return passedTime >= Math.max(0, timePreempt - objectHittableRange);
    }

    private boolean isHit() {
        for (int i = 0, count = listener.getCursorsCount(); i < count; i++) {

            var inPosition = Utils.squaredDistance(pos, listener.getMousePos(i)) <= radiusSquared;
            if (GameHelper.isRelaxMod() && passedTime - timePreempt >= 0 && inPosition) {
                return true;
            }

            var isPressed = listener.isMousePressed(this, i);
            if (isPressed && inPosition) {
                return true;
            } else if (GameHelper.isAutopilotMod() && isPressed) {
                return true;
            }
        }
        return false;
    }

    private double hitOffsetToPreviousFrame() {
        // 因为这里是阻塞队列, 所以提前点的地方会影响判断
        for (int i = 0, count = listener.getCursorsCount(); i < count; i++) {

            var inPosition = Utils.squaredDistance(pos, listener.getMousePos(i)) <= radiusSquared;
            if (GameHelper.isRelaxMod() && passedTime - timePreempt >= 0 && inPosition) {
                return 0;
            }

            var isPressed = listener.isMousePressed(this, i);
            if (isPressed && inPosition) {
                return listener.downFrameOffset(i);
            } else if (GameHelper.isAutopilotMod() && isPressed) {
                return 0;
            }
        }
        return 0;
    }


    @Override
    public void update(final float dt) {
        // PassedTime < 0 means circle logic is over
        if (passedTime < 0) {
            return;
        }
        // If we have clicked circle
        if (replayObjectData != null) {
            if (passedTime - timePreempt + dt / 2 > replayObjectData.accuracy / 1000f) {
                final float acc = Math.abs(replayObjectData.accuracy / 1000f);
                if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getOverallDifficulty())) {
                    playSound();
                }
                listener.registerAccuracy(replayObjectData.accuracy / 1000f);
                passedTime = -1;
                // Remove circle and register hit in update thread
                listener.onCircleHit(id, replayObjectData.accuracy / 1000f, pos,endsCombo, replayObjectData.result, comboColor);
                removeFromScene();
                return;
            }
        } else if (canBeHit() && isHit()) {
            float signAcc = passedTime - timePreempt;
            if (Config.isFixFrameOffset()) {
                signAcc += (float) hitOffsetToPreviousFrame() / 1000f;
            }
            final float acc = Math.abs(signAcc);
            if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getOverallDifficulty())) {
                playSound();
            }
            listener.registerAccuracy(signAcc);
            passedTime = -1;
            // Remove circle and register hit in update thread
            float finalSignAcc = signAcc;
            startHit = true;
            listener.onCircleHit(id, finalSignAcc, pos, endsCombo, (byte) 0, comboColor);
            removeFromScene();
            return;
        }

        if (GameHelper.isKiai()) {
            final float kiaiModifier = (float) Math.max(0, 1 - GameHelper.getCurrentBeatTime() / GameHelper.getBeatLength()) * 0.5f;
            final float r = Math.min(1, comboColor.r() + (1 - comboColor.r()) * kiaiModifier);
            final float g = Math.min(1, comboColor.g() + (1 - comboColor.g()) * kiaiModifier);
            final float b = Math.min(1, comboColor.b() + (1 - comboColor.b()) * kiaiModifier);
            kiai = true;
            circle.setColor(r, g, b);
        } else if (kiai) {
            circle.setColor(comboColor.r(), comboColor.g(), comboColor.b());
            kiai = false;
        }

        passedTime += dt;

        // We are still at approach time. Let entity modifiers finish first.
        if (passedTime < timePreempt) {
            return;
        }

        if (autoPlay) {
            playSound();
            passedTime = -1;
            // Remove circle and register hit in update thread
            listener.onCircleHit(id, 0, pos, endsCombo, ResultType.HIT300.getId(), comboColor);
            removeFromScene();
        } else {
            approachCircle.clearEntityModifiers();
            approachCircle.setAlpha(0);

            // If passed too much time, counting it as miss
            if (passedTime > timePreempt + GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getOverallDifficulty())) {
                passedTime = -1;
                final byte forcedScore = (replayObjectData == null) ? 0 : replayObjectData.result;

                removeFromScene();
                listener.onCircleHit(id, 10, pos, false, forcedScore, comboColor);
            }
        }
    } // update(float dt)

    @Override
    public void tryHit(final float dt) {
        if (canBeHit() && isHit()) {
            float signAcc = passedTime - timePreempt;
            if (Config.isFixFrameOffset()) {
                signAcc += (float) hitOffsetToPreviousFrame() / 1000f;
            }
            final float acc = Math.abs(signAcc);
            if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getOverallDifficulty())) {
                playSound();
            }
            listener.registerAccuracy(signAcc);
            passedTime = -1;
            // Remove circle and register hit in update thread
            float finalSignAcc = signAcc;
            listener.onCircleHit(id, finalSignAcc, pos, endsCombo, (byte) 0, comboColor);
            removeFromScene();
        }
    }
}
