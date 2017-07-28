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

import java.util.function.Supplier;
import javax.annotation.Nullable;

import reactor.core.Disposable;

/**
 * A {@link Disposable} container that allows updating/replacing its inner Disposable
 * atomically and with respect of disposing the container itself.
 *
 * @author Simon Baslé
 */
public interface SequentialDisposable extends Disposable, Supplier<Disposable> {

	/**
	 * Atomically push the next {@link Disposable} on this container and dispose the previous
	 * one (if any). If the container has been disposed, fall back to disposing {@code next}.
	 *
	 * @param next the {@link Disposable} to push, may be null
	 * @return true if the operation succeeded, false if the container has been disposed
	 * @see #replace(Disposable)
	 */
	boolean update(Disposable next);

	/**
	 * Atomically push the next {@link Disposable} on this container but don't dispose the previous
	 * one (if any). If the container has been disposed, fall back to disposing {@code next}.
	 *
	 * @param next the {@link Disposable} to push, may be null
	 * @return true if the operation succeeded, false if the container has been disposed
	 * @see #update(Disposable)
	 */
	boolean replace(@Nullable Disposable next);
}
