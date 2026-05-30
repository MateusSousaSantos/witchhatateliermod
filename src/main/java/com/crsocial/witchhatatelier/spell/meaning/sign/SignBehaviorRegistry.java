package com.crsocial.witchhatatelier.spell.meaning.sign;

import com.crsocial.witchhatatelier.spell.compiler.SignType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps {@link SignType} to an optional {@link SignBehavior} implementation.
 * Most signs have NO custom behaviour (they rely on the matrix system alone);
 * only signs with unique mechanics register here.
 *
 * <p>The meaning engine calls {@link #find(SignType)} for each bundle; when
 * empty, no custom modification is applied.</p>
 */
public final class SignBehaviorRegistry {

    private static final SignBehaviorRegistry INSTANCE = new SignBehaviorRegistry();

    private final Map<SignType, SignBehavior> behaviors = new EnumMap<>(SignType.class);

    static {
        bootstrap();
    }

    private SignBehaviorRegistry() {}

    public static SignBehaviorRegistry get() {
        return INSTANCE;
    }

    private static void bootstrap() {
        // Only signs with unique implementations register here.
        // Signs not listed use the matrix system exclusively.
        INSTANCE.register(SignType.LEVITATION, new LevitationSignBehavior());
    }

    public void register(SignType type, SignBehavior behavior) {
        behaviors.put(type, behavior);
    }

    /**
     * Returns the custom behaviour for the given sign type, or empty if this
     * sign relies solely on the matrix system.
     */
    public Optional<SignBehavior> find(SignType type) {
        return Optional.ofNullable(behaviors.get(type));
    }
}

