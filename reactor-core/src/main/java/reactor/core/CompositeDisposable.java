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

package reactor.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import reactor.util.concurrent.OpenHashSet;

/**
 * A container of {@link Disposable} that is itself {@link Disposable}. Atomically
 * add and remove disposable, and dispose them all in one go by either using {@link #clear()}
 * (allowing further reuse of the container) or {@link #dispose()} (disallowing further
 * reuse of the container).
 * <p>
 * Two removal operations are offered: {@link #delete(Disposable)} will NOT call
 * {@link Disposable#dispose()} on the element removed from the container, while
 * {@link #remove(Disposable)} will.
 *
 * @author Simon Baslé
 * @author David Karnok
 */
public class CompositeDisposable implements Disposable {

	OpenHashSet<Disposable> disposables;
	volatile boolean disposed;

	/**
	 * Creates an empty {@link CompositeDisposable}.
	 */
	public CompositeDisposable() {
	}

	/**
	 * Creates a {@link CompositeDisposable} with the given array of initial elements.
	 * @param disposables the array of {@link Disposable} to start with
	 */
	public CompositeDisposable(Disposable... disposables) {
		Objects.requireNonNull(disposables, "disposables is null");
		this.disposables = new OpenHashSet<>(disposables.length + 1, 0.75f);
		for (Disposable d : disposables) {
			Objects.requireNonNull(d, "Disposable item is null");
			this.disposables.add(d);
		}
	}

	/**
	 * Creates a {@link CompositeDisposable} with the given {@link Iterable} sequence of
	 * initial elements.
	 * @param disposables the Iterable sequence of {@link Disposable} to start with
	 */
	public CompositeDisposable(Iterable<? extends Disposable> disposables) {
		Objects.requireNonNull(disposables, "disposables is null");
		this.disposables = new OpenHashSet<>();
		for (Disposable d : disposables) {
			Objects.requireNonNull(d, "Disposable item is null");
			this.disposables.add(d);
		}
	}

	/**
	 * Atomically mark the container as {@link #isDisposed() disposed}, clear it and then
	 * dispose all the previously contained Disposables. From there on the container cannot
	 * be reused, as {@link #add(Disposable)} and {@link #addAll(Disposable...)} methods
	 * will immediately return {@literal false}. Use {@link #clear()} instead if you want
	 * to reuse the container.
	 *
	 * @see #clear()
	 */
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

	/**
	 * Indicates if the container has already been disposed.
	 * <p>Note that if that is the case, attempts to add new disposable to it via
	 * {@link #add(Disposable)} and {@link #addAll(Disposable...)} will be rejected.
	 *
	 * @return true if the container has been disposed, false otherwise.
	 */
	@Override
	public boolean isDisposed() {
		return disposed;
	}

	/**
	 * Add a {@link Disposable} to this container, if it is not {@link #isDisposed() disposed}.
	 * Otherwise d is disposed immediately.
	 *
	 * @param d the {@link Disposable} to add.
	 * @return true if the disposable could be added, false otherwise.
	 */
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

	/**
	 * Atomically adds the given array of Disposables to the container or disposes them
	 * all if the container has been disposed.
	 * @param ds the array of Disposables
	 * @return true if the operation was successful, false if the container has been disposed
	 */
	public boolean addAll(Disposable... ds) {
		Objects.requireNonNull(ds, "ds is null");
		if (!disposed) {
			synchronized (this) {
				if (!disposed) {
					OpenHashSet<Disposable> set = disposables;
					if (set == null) {
						set = new OpenHashSet<>(ds.length + 1, 0.75f);
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

	/**
	 * Delete the {@link Disposable} from this container, without disposing it.
	 *
	 * @param d the {@link Disposable} to delete.
	 * @return true if the disposable was successfully deleted, false otherwise.
	 * @see #remove(Disposable)
	 */
	public boolean delete(Disposable d) {
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

	/**
	 * Remove the {@link Disposable} from this container, that is delete it from the
	 * container and dispose it via {@link Disposable#dispose() dispose()} once deleted.
	 *
	 * @param d the {@link Disposable} to remove and dispose.
	 * @return true if the disposable was successfully removed and disposed, false otherwise.
	 * @see #delete(Disposable)
	 */
	public boolean remove(Disposable d) {
		if (delete(d)) {
			d.dispose();
			return true;
		}
		return false;
	}

	/**
	 * Atomically clears the container, then disposes all the previously contained Disposables.
	 * Unlike with {@link #dispose()}, the container can still be used after that.
	 */
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

	/**
	 * Returns the number of currently held Disposables.
	 * @return the number of currently held Disposables
	 */
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
