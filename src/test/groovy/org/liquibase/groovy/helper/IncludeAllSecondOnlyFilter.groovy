/*
 * Copyright 2011-2017 Tim Berglund and Steven C. Saliman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.liquibase.groovy.helper

import liquibase.changelog.IncludeAllFilter

/**
 * This class is a helper for the {@code DatabaseChangeLogDelegateTests} class.
 * It is an implementation of the Liquibase {@code IncludeAllFilter} that
 * includes all files that have "first" in the name.
 *
 * @author Steven C. Saliman
 */
@SuppressWarnings("unused") // It's used via reflection.
class IncludeAllFirstOnlyFilter implements IncludeAllFilter {
	@Override
	boolean include(String changeLogPath) {
		return changeLogPath.contains("first")
	}
}
