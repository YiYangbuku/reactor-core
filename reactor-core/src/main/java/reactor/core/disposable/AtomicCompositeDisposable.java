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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.util.concurrent.OpenHashSet;

/**
 * A {@link CompositeDisposable} that allows to atomically add, remove and mass dispose.
 *
 * @author Simon Baslé
 * @author David Karnok
 */
final class AtomicCompositeDisposable implements CompositeDisposable<Disposable> {

	OpenHashSet<Disposable> disposables;
	volatile boolean disposed;

	/**
	 * Creates an empty {@link AtomicCompositeDisposable}.
	 */
	public AtomicCompositeDisposable() {
	}

	/**
	 * Creates a {@link AtomicCompositeDisposable} with the given array of initial elements.
	 * @param disposables the array of {@link Disposable} to start with
	 */
	public AtomicCompositeDisposable(Disposable... disposables) {
		Objects.requireNonNull(disposables, "disposables is null");
		this.disposables = new OpenHashSet<>(disposables.length + 1, 0.75f);
		for (Disposable d : disposables) {
			Objects.requireNonNull(d, "Disposable item is null");
			this.disposables.add(d);
		}
	}

	/**
	 * Creates a {@link AtomicCompositeDisposable} with the given {@link Iterable} sequence of
	 * initial elements.
	 * @param disposables the Iterable sequence of {@link Disposable} to start with
	 */
	public AtomicCompositeDisposable(Iterable<? extends Disposable> disposables) {
		Objects.requireNonNull(disposables, "disposables is null");
		this.disposables = new OpenHashSet<>();
		for (Disposable d : disposables) {
			Objects.requireNonNull(d, "Disposable item is null");
			this.disposables.add(d);
		}
	}

	@Override
	public void dispose() {
		if (disposed) {
			return;
		}
		OpenHashSet<Disposable> set;
		synchronized (this) {
			if (disposed) {
				return;
			}
			disposed = true;
			set = disposables;
			disposables = null;
		}

		dispose(set);
	}

	@Override
	public boolean isDisposed() {
		return disposed;
	}

	@Override
	public boolean add(Disposable d) {
		Objects.requireNonNull(d, "d is null");
		if (!disposed) {
			synchronized (this) {
				if (!disposed) {
					OpenHashSet<Disposable> set = disposables;
					if (set == null) {
						set = new OpenHashSet<>();
						disposables = set;
					}
					set.add(d);
					return true;
				}
			}
		}
		d.dispose();
		return false;
	}

	@Override
	public boolean addAll(Collection<Disposable> ds) {
		Objects.requireNonNull(ds, "ds is null");
		if (!disposed) {
			synchronized (this) {
				if (!disposed) {
					OpenHashSet<Disposable> set = disposables;
					if (set == null) {
						set = new OpenHashSet<>(ds.size() + 1, 0.75f);
						disposables = set;
					}
					for (Disposable d : ds) {
						Objects.requireNonNull(d, "d is null");
						set.add(d);
					}
					return true;
				}
			}
		}
		for (Disposable d : ds) {
			d.dispose();
		}
		return false;
	}

	@Override
	public boolean remove(Disposable d) {
		Objects.requireNonNull(d, "Disposable item is null");
		if (disposed) {
			return false;
		}
		synchronized (this) {
			if (disposed) {
				return false;
			}

			OpenHashSet<Disposable> set = disposables;
			if (set == null || !set.remove(d)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void clear() {
		if (disposed) {
			return;
		}
		OpenHashSet<Disposable> set;
		synchronized (this) {
			if (disposed) {
				return;
			}

			set = disposables;
			disposables = null;
		}

		dispose(set);
	}

	@Override
	public int size() {
		if (disposed) {
			return 0;
		}
		synchronized (this) {
			if (disposed) {
				return 0;
			}
			OpenHashSet<Disposable> set = disposables;
			return set != null ? set.size() : 0;
		}
	}

	/**
	 * Dispose the contents of the OpenHashSet by suppressing non-fatal
	 * Throwables till the end.
	 * @param set the OpenHashSet to dispose elements of
	 */
	void dispose(@Nullable OpenHashSet<Disposable> set) {
		if (set == null) {
			return;
		}
		List<Throwable> errors = null;
		Object[] array = set.keys();
		for (Object o : array) {
			if (o instanceof Disposable) {
				try {
					((Disposable) o).dispose();
				} catch (Throwable ex) {
					Exceptions.throwIfFatal(ex);
					if (errors == null) {
						errors = new ArrayList<>();
					}
					errors.add(ex);
				}
			}
		}
		if (errors != null) {
			if (errors.size() == 1) {
				throw Exceptions.propagate(errors.get(0));
			}
			throw Exceptions.multiple(errors);
		}
	}
}