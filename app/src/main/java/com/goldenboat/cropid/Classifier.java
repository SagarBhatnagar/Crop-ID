package com.goldenboat.cropid;

public interface Classifier {
    String name();

    Classification recognize(final float[] pixels);
}
