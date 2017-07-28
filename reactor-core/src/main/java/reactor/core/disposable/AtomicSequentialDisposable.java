/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.disposable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import reactor.core.Disposable;

/**
 * Not public API. Implementation of a {@link SequentialDisposable}.
 *
 * @author Simon Baslé
 * @author David Karnok
 */
final class AtomicSequentialDisposable implements SequentialDisposable {

	volatile Disposable inner;
	static final AtomicReferenceFieldUpdater<AtomicSequentialDisposable, Disposable>
			INNER =
			AtomicReferenceFieldUpdater.newUpdater(AtomicSequentialDisposable.class, Disposable.class, "inner");

	@Override
	public boolean update(Disposable next) {
		return Disposables.set(INNER, this, next);
	}

	@Override
	public boolean replace(@Nullable Disposable next) {
		return Disposables.replace(INNER, this, next);
	}

	@Override
	@Nullable
	public Disposable get() {
		return inner;
	}

	@Override
	public void dispose() {
		Disposables.dispose(INNER, this);
	}

	@Override
	public boolean isDisposed() {
		return Disposables.isDisposed(INNER.get(this));
	}
}