package lib.location.runtime;



import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import lib.location.Cancellable;


public final class Cancellables {
    private Cancellables(){}

    /** owner'ın yaşam döngüsünde event geldiğinde iptal eder. */
    @NonNull
    public static Cancellable bindUntil(@NonNull LifecycleOwner owner,
                                        @NonNull final Cancellable c,
                                        @NonNull final Lifecycle.Event event) {
        owner.getLifecycle().addObserver((LifecycleEventObserver) (source, e) -> {
            if (e == event) c.cancel();
        });
        return c;
    }

    /** Fragment'larda view ömrüne bağlı iptal. */
    @NonNull
    public static Cancellable bindToDestroyView(@NonNull LifecycleOwner viewLifecycleOwner,
                                                @NonNull Cancellable c) {
        return bindUntil(viewLifecycleOwner, c, Lifecycle.Event.ON_DESTROY);
        // Not: Fragment'larda ViewLifecycleOwner kullanmak önerilir.
    }

    /** Activity'lerde onStop'ta iptal etmek için. */
    @NonNull
    public static Cancellable bindToStop(@NonNull LifecycleOwner owner,
                                         @NonNull Cancellable c) {
        return bindUntil(owner, c, Lifecycle.Event.ON_STOP);
    }
}
