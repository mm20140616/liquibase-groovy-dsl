/*
 * Copyright 2011-2015 Tim Berglund and Steven C. Saliman
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

package liquibase.parser.groovy.delegate

import liquibase.JUnitScope
import liquibase.Scope
import liquibase.changelog.ChangeLog
import liquibase.exception.ParseException
import liquibase.parser.ParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.FileSystemResourceAccessor

//import liquibase.changelog.ChangeLogParameters
//import liquibase.changelog.DatabaseChangeLog
//import liquibase.database.ObjectQuotingStrategy
//import liquibase.exception.ChangeLogParseException
//import liquibase.parser.ChangeLogParser
//import liquibase.parser.ChangeLogParserFactory
//import liquibase.parser.groovy.GroovyLiquibaseChangeLogParser
//import liquibase.precondition.Precondition
//import liquibase.precondition.core.DBMSPrecondition
//import liquibase.precondition.core.PreconditionContainer
//import liquibase.precondition.core.RunningAsPrecondition
//import liquibase.resource.ClassLoaderResourceAccessor
//import liquibase.resource.FileSystemResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field

//
//import java.lang.reflect.Field
//
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * Test class for the {@link DatabaseChangeLogDelegate}
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateTests extends IntegrationTest {
	// TODO: test the context attribute of the databaseChangelog itself
	static final def FILE_PATH = "src/test/changelog"
	static final def RESOURCE_PATH = "classpath:changelog"  // in resources
	static final def TMP_CHANGELOG_DIR = new File("${FILE_PATH}/tmp")
	static final def TMP_INCLUDE_DIR = new File("${TMP_CHANGELOG_DIR}/include")
	static final def EMPTY_CHANGELOG = "${FILE_PATH}/empty-changelog.groovy"
	static final def SIMPLE_CHANGELOG = "${FILE_PATH}/simple-changelog.groovy"
	static final def ROOT_CHANGE_SET = 'root-change-set'
	static final def FIRST_INCLUDED_CHANGE_SET = 'included-change-set-1'
	static final def SECOND_INCLUDED_CHANGE_SET = 'included-change-set-2'

	def resourceAccessor
	ParserFactory parserFactory
	def scope


	@Before
	void registerParser() {
		// For most tests, we want a resourceAccessor that has a null base
		// directory, like the one Liquibase will give us.
		resourceAccessor = new FileSystemResourceAccessor()
		scope = JUnitScope.instance.child(Scope.Attr.resourceAccessor, resourceAccessor)
//		parserFactory = ParserFactory.instance
//		ParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
		// make sure we start with clean temporary directories before each test
		TMP_CHANGELOG_DIR.deleteDir()
		TMP_INCLUDE_DIR.mkdirs()
	}

	/**
	 * Attempt to clean up included files and directories.  We do this every time
	 * to make sure we start clean each time.  The includeAll test depends on it.
	 */
	@After
	void cleanUp() {
		TMP_CHANGELOG_DIR.deleteDir()
	}

	/**
	 * Test parsing an empty changelog file.
	 */
	// TODO: This doesn't need to load a real file...
	@Test
	void parseEmptyChangelog() {
		// For the parse test, we need a resourceAccessor that points to the
		// current working directory.
		resourceAccessor = new FileSystemResourceAccessor('.')
		def changeLog = scope.getSingleton(ParserFactory.class).parse(EMPTY_CHANGELOG, ChangeLog.class, scope);
		assertNotNull "Parsed ChangeLog was null", changeLog
		assertTrue "Parser result was not a ChangeLog", changeLog instanceof ChangeLog
		assertEquals 0, changeLog.changeLogEntries.size()
	}

// TODO: this doesn't need to be a real file.
	@Test
	void parseSimpleChangelog() {
		// For the parse test, we need a resourceAccessor that points to the
		// current working directory.
		resourceAccessor = new FileSystemResourceAccessor('.')
		def changeLog = scope.getSingleton(ParserFactory.class).parse(SIMPLE_CHANGELOG, ChangeLog.class, scope);
		assertNotNull "Parsed ChangeLog was null", changeLog
		assertTrue "Parser result was not a ChangeLog", changeLog instanceof ChangeLog

		def changeSets = changeLog.changeSets
		assertEquals 1, changeSets.size()
		def changeSet = changeSets[0]
		assertNotNull "ChangeSet was null", changeSet
		assertEquals 'tlberglund', changeSet.author
		assertEquals 'change-set-001', changeSet.id
		// Test to make sure logical path is set.
	}


	@Test(expected = ParseException)
	void parsingEmptyDatabaseChangeLogFails() {
		// For the parse test, we need a resourceAccessor that points to the
		// current working directory.
		resourceAccessor = new FileSystemResourceAccessor('.')
		def changeLog = parseChangeLog("""
			databaseChangeLog()
		""")

		assertNotNull changelog
	}

// TODO: Consider removing support for this feature.
	@Test
	void parsingDatabaseChangeLogAsProperty() {
		// For the parse test, we need a resourceAccessor that points to the
		// current working directory.
		resourceAccessor = new FileSystemResourceAccessor('.')
		def changeLog = parseChangeLog("""
            databaseChangeLog = {
            }
        """)

		assertNotNull "Parsed ChangeLog was null", changeLog
	}


	@Test
	void preconditionParameters() {
		def changeLog = parseChangeLog("""
			databaseChangeLog() {
				preConditions(onFail: 'WARN',
							  onError: 'MARK_RAN',
							  onUpdateSQL: 'TEST',
							  onFailMessage: 'fail-message!!!1!!1one!',
							   onErrorMessage: 'error-message')
			}
		""")


		def preconditions = changeLog.preconditions
		assertNotNull preconditions
//		assertTrue preconditions instanceof PreconditionContainer
		assertEquals PreconditionContainer.FailOption.WARN, preconditions.onFail
		assertEquals PreconditionContainer.ErrorOption.MARK_RAN, preconditions.onError
		assertEquals PreconditionContainer.OnSqlOutputOption.TEST, preconditions.onSqlOutput
		assertEquals 'fail-message!!!1!!1one!', preconditions.onFailMessage
		assertEquals 'error-message', preconditions.onErrorMessage
	}


	/**
	 * Test creating a changeSet with no attributes. This verifies that we use
	 * expected default values when a value is not provided.
	 */
	@Test
	void changeSetEmpty() {
		def changeLog = buildChangeLog {
			changeSet([:]) {}
		}
		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertNull changeLog.changeSets[0].id
		assertNull changeLog.changeSets[0].author
		assertFalse changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
		assertFalse changeLog.changeSets[0].runOnChange
		assertEquals FILE_PATH, changeLog.changeSets[0].filePath
		assertEquals 0, changeLog.changeSets[0].contexts.contexts.size()
		assertNull changeLog.changeSets[0].labels
		assertNull changeLog.changeSets[0].dbmsSet
		assertTrue changeLog.changeSets[0].runInTransaction
	    assertNull changeLog.changeSets[0].failOnError
	    assertEquals "HALT", changeLog.changeSets[0].onValidationFail.toString()
		assertNull changeLog.changeSets[0].objectQuotingStrategy
		assertNull changeLog.changeSets[0].created
		assertNull changeLog.changeSets[0].runOrder
	}

	/**
	 * Test creating a changeSet with all supported attributes.
	 */
	@Test
	void changeSetFull() {
		def changeLog = buildChangeLog {
			changeSet(id: 'monkey-change',
					  author: 'stevesaliman',
					  dbms: 'mysql',
					  runAlways: true,
					  runOnChange: true,
					  context: 'testing',
					  labels: 'test_label',
					  runInTransaction: false,
					  failOnError: true,
					  onValidationFail: "MARK_RAN",
					  objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
                      created: 'today',
                      runOrder: 'first') {
			  dropTable(tableName: 'monkey')
			}
		}

//		ObjectQuotingStrategy o = ObjectQuotingStrategy.valueOf("LEGACY")
		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertEquals 'monkey-change', changeLog.changeSets[0].id
		assertEquals 'stevesaliman', changeLog.changeSets[0].author
		assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
		assertTrue changeLog.changeSets[0].runOnChange
		assertEquals FILE_PATH, changeLog.changeSets[0].filePath
		assertEquals 'testing', changeLog.changeSets[0].contexts.contexts.toArray()[0]
		assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
		assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
		assertFalse changeLog.changeSets[0].runInTransaction
		assertTrue changeLog.changeSets[0].failOnError
		assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
		assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
		assertEquals 'today', changeLog.changeSets[0].created
		assertEquals 'first', changeLog.changeSets[0].runOrder
	}

	/**
	 * Test creating a changeSet with an unsupported attribute.
	 */
	@Test(expected = ParseException)
	void changeSetInvalidAttribute() {
		buildChangeLog {
			changeSet(id: 'monkey-change',
					  author: 'stevesaliman',
					  dbms: 'mysql',
					  runAlways: false,
					  runOnChange: true,
					  context: 'testing',
					  labels: 'test_label',
					  runInTransaction: false,
					  failOnError: true,
					  onValidationFail: "MARK_RAN",
					  objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
					  created: 'today',
					  runOrder: 'first',
			          invalidAttribute: 'invalid') {
				dropTable(tableName: 'monkey')
			}
		}
	}

	/**
	 * Test creating a changeSet with an unsupported Object quoting strategy.
	 */
	@Test(expected = ParseException)
	void changeSetInvalidQuotingStrategy() {
		buildChangeLog {
			changeSet(id: 'monkey-change',
					author: 'stevesaliman',
					dbms: 'mysql',
					runAlways: false,
					runOnChange: true,
					context: 'testing',
					labels: 'test_label',
					runInTransaction: false,
					failOnError: true,
					onValidationFail: "MARK_RAN",
					objectQuotingStrategy: "MONKEY_QUOTING") {
				dropTable(tableName: 'monkey')
			}
		}
	}

	/**
	 * Test change log preconditions.  This uses the same delegate as change set
	 * preconditions, so we don't have to do much here, just make sure we can
	 * call the correct thing from a change log and have the change log altered.
	 */
	@Test
	void preconditionsInChangeLog() {
		def changeLog = buildChangeLog {
			preConditions {
				dbms(type: 'mysql')
			}
		}

		assertEquals 0, changeLog.changeSets.size()
		assertNotNull changeLog.preconditions
//		assertTrue changeLog.preconditions.nestedPreconditions.every { precondition -> precondition instanceof Precondition }
		assertEquals 1, changeLog.preconditions.nestedPreconditions.size()
//		assertTrue changeLog.preconditions.nestedPreconditions[0] instanceof DBMSPrecondition
		assertEquals 'mysql', changeLog.preconditions.nestedPreconditions[0].type

	}

	/**
	 * Test including a file when we have an unsupported attribute.
	 */
	@Test(expected = ParseException)
	void includeInvalidAttribute() {
		buildChangeLog {
			include(changeFile: 'invalid')
		}
	}

	/**
	 * Try including a file.
	 */
	@Test
	void includeValid() {
		def includedChangeLogFile = createFileFrom(TMP_INCLUDE_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.canonicalPath
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '${includedChangeLogFile}', context: 'all')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including a file relative to the changelolg file.
	 */
	@Test
	void includeRelative() {
		def includedChangeLogFile = createFileFrom(TMP_INCLUDE_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.name

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: 'include/${includedChangeLogFile}', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Test including a path when we have an unsupported attribute.
	 */
	@Test(expected = ParseException)
	void includeAllInvalidAttribute() {
		buildChangeLog {
			includeAll(changePath: 'invalid')
		}
	}

	/**
	 * Try including all files in a directory.  For this test, we want 2 files
	 * to make sure we include them both, and in the right order.  Note: when
	 * other tests throw exceptions, this test may also fail because of unclean
	 * directories.  Fix the other tests first.
	 */
	@Test
	void includeAllValid() {
		def includedChangeLogDir = createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${includedChangeLogDir}', context: 'all')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all files in a directory, but with a resourceFilter.
	 * For this test, we'll repeat want 2 files, but with a filter that
	 * excludes one of them. Test may fail because of unclean directories.
	 * Fix the other tests first.
	 */
	@Test
	void includeAllValidWithFilter() {
		def includedChangeLogDir = createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${includedChangeLogDir}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals ROOT_CHANGE_SET, changeSets[1].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all files in a directory relative to the changelog.
	 */
	@Test
	void includeAllRelative() {
		createIncludedChangeLogFiles()
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: 'include', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all when the path doesn't exist is invalid.  Expect an error.
	 */
	@Test(expected = ParseException)
	void includeAllInvalidPath() {
		buildChangeLog {
			includeAll(path: 'invalid')
		}
	}

	/**
	 * Try including all when the path doesn't exist is invalid, but we've
	 * set the errorIfMissingOrEmpty property to false.  For this test, we'll
	 * use a string to represent falseness.
	 */
	@Test
	void includeAllInvalidPathIgnoreError() {
		def changeLog = buildChangeLog {
			includeAll(path: 'invalid', errorIfMissingOrEmpty: false)
		}
		assertNotNull changeLog
		def changeSets = changeLog.changeSets
		assertNotNull changeSets
		assertEquals 0, changeSets.size()
	}

	/**
	 * Try including all when the path is valid, but there are no usable files
	 * in the directory.  We'll test this by using the filter to eliminate the
	 * one change set we'll create to make sure we do the test after the filter.
	 */
	@Test(expected = ParseException)
	void includeAllEmptyPath() {
		// This file should be excluded by the resource filter.
		def includedChangeLogFile = createFileFrom(TMP_INCLUDE_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.parentFile.canonicalPath
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${includedChangeLogFile}',
             resourceFilter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all when the path is valid, but there are no usable files
	 * in the directory.  This time, we'll set the errorIfMissingOrEmpty
	 * property to false.  For this test, we'll use a boolean to represent
	 * falseness.  We should get ignore the error about the empty directory,
	 * and get the root change set from the parent file.
	 */
	@Test
	void includeAllEmptyPathIgnoreError() {
		// This file should be excluded by the resource filter.
		def includedChangeLogFile = createFileFrom(TMP_INCLUDE_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.parentFile.canonicalPath
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${includedChangeLogFile}', errorIfMissingOrEmpty: false,
             resourceFilter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 1, changeSets.size()  // from the changelog itself.
		assertEquals ROOT_CHANGE_SET, changeSets[0].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 1, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
	}

	//----------------------------------------------------------------------
	// Tests of the includeAll method when the changelog file is accessed
	// via the classpath.


	/**
	 * Try including all files in a classpath directory.  We'll want to make
	 * sure we include them both, and in the right order.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test
	void includeAllValidClasspath() {
		def rootChangeLogFile = "changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all files in a classpath directory, but with a
	 * resourceFilter. For this test, we'll have 2 files in the directory, but
	 * the resource filter will excludes one of them.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test
	void includeAllValidClasspathWithFilter() {
		def rootChangeLogFile = "filtered-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals ROOT_CHANGE_SET, changeSets[1].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
//		assertTrue preconditions[1] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all from a classpath loaded change log when the include
	 * path doesn't exist is invalid.  Expect an error.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test(expected = ParseException)
	void includeAllInvalidClassPath() {
		def rootChangeLogFile = "invalid-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
//		parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all from a classpath loaded change log when the include
	 * path is invalid, but we've set the errorIfMissingOrEmpty property to
	 * false.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test
	void includeAllInvalidClassPathIgnoreError() {
		def rootChangeLogFile = "ignore-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
//		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 1, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals ROOT_CHANGE_SET, changeSets[0].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 1, preconditions.size()
//		assertTrue preconditions[0] instanceof DBMSPrecondition
	}

	/**
	 * Try adding a property with an invalid attribute
	 */
	@Test(expected = ParseException)
	void propertyInvalidAttribute() {
		buildChangeLog {
			property(propertyName: 'invalid', propertyValue: 'invalid')
		}
	}

	/**
	 * Try creating an empty property.
	 */
	@Test
	void propertyEmpty() {
		def changeLog = buildChangeLog {
			property([:])
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertNull property.key
		assertNull property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try creating a property with a name and value only.  Make sure we don't
	 * try to set the database or contexts
	 */
	@Test
	void propertyPartial() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry')
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try creating a property with all supported attributes, and a boolean for
	 * the global attribute.
	 */
	@Test
	void propertyFullBooleanGlobal() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry', dbms: 'mysql', labels: 'test_label', context: 'test', 'global': true)
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
	}

	/**
	 * Try creating a property with all supported attributes and a String for
	 * the global attribute.
	 */
	@Test
	void propertyFullStringGlobal() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry', dbms: 'mysql', labels: 'test_label', context: 'test', 'global': 'true')
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
	}

	/**
	 * Try including a property from a file that doesn't exist.
	 */
	@Test(expected = ParseException)
	void propertyFromInvalidFile() {
		def changeLog = buildChangeLog {
			property(file: "${TMP_CHANGELOG_DIR}/bad.properties")
		}
	}

	/**
	 * Try including a property from a file when we don't hae a dbms or context.
	 */
	@Test
	void propertyFromFilePartial() {
		def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
		propertyFile = propertyFile.canonicalPath
		propertyFile = propertyFile.replaceAll("\\\\", "/")

		def changeLog = buildChangeLog {
			property(file: "${propertyFile}")
		}


		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try including a property from a file when we do have a context and dbms..
	 */
	@Test
	void propertyFromFileFull() {
		def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
		propertyFile = propertyFile.canonicalPath
		propertyFile = propertyFile.replaceAll("\\\\", "/")

		def changeLog = buildChangeLog {
			property(file: "${propertyFile}", dbms: 'mysql', context: 'test', labels: 'test_label')
		}


		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
	}

	/**
	 * Helper method that builds a changeSet from the given closure.  Tests will
	 * use this to test parsing the various closures that make up the Groovy DSL.
	 * @param closure the closure containing changes to parse.
	 * @return the changeSet, with parsed changes from the closure added.
	 */
	private def buildChangeLog(Closure closure) {
		def changelog = new ChangeLog(FILE_PATH)
//		changelog.changeLogParameters = new ChangeLogParameters()
		closure.delegate = new DatabaseChangeLogDelegate(changelog)
		closure.delegate.resourceAccessor = resourceAccessor
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()
		return changelog
	}

	/**
	 * Helper method to create changelogs in a directory for testing the
	 * includeAll methods.  It creates 3 files:
	 * <ul>
	 * <li>2 groovy files that should be included with an includeAll</li>
	 * <li>An xml file that should be excluded from the includeAll</li>
	 * </ul>
	 * @return the full path of the directory where the files were placed.
	 */
	private String createIncludedChangeLogFiles() {
		createFileFrom(TMP_INCLUDE_DIR, 'first', '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: '${FIRST_INCLUDED_CHANGE_SET}') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		createFileFrom(TMP_INCLUDE_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

		createFileFrom(TMP_INCLUDE_DIR, 'third', '-3.xml', """
<databaseChangeLog>
  <changeSet author="ssaliman" id="included-change-set-3">
    <addColumn tableName="monkey">
      <column name="gender" type="varchar(1)"/>
    </addColumn>
  </changeSet>
</databaseChangeLog>
""")

		return TMP_INCLUDE_DIR.canonicalPath.replaceAll("\\\\", "/")
	}

	private File createFileFrom(directory, suffix, text) {
		createFileFrom(directory, 'liquibase-', suffix, text)
	}

	private File createFileFrom(directory, prefix, suffix, text) {
		def file = File.createTempFile(prefix, suffix, directory)
		file << text
	}
}

