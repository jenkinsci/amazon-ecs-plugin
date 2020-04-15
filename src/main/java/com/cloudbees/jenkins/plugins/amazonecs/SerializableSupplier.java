package com.cloudbees.jenkins.plugins.amazonecs;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * @author cbongiorno on 2020-04-13.
 * This is necessary to overcome the fact that jenkins needs everything serializable, but we also don't want it to attempt to integrate with AWS
 * We need to allow for mocks in our tests but the real thing in prod
 */
@FunctionalInterface
public interface SerializableSupplier<T> extends Supplier<T>, Serializable {
}
