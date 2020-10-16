package com.zebrunner.jenkins.jobdsl.factory.view

import com.zebrunner.jenkins.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class ListViewFactory extends DslFactory {
    def folder
    def name
    def descFilter
    def nameFilter

    public ListViewFactory(folder, name, descFilter) {
        this(folder, name, descFilter, "")
    }
    
    public ListViewFactory(folder, name, descFilter, nameFilter) {
        this.folder = folder
        this.name = name
        this.descFilter = descFilter
        this.nameFilter = nameFilter
    }

    def create() {
        //TODO: reuse getFullName
        def view = _dslFactory.listView("${folder}/${name}")
        view.with {
            columns {
                status()
                weather()
                name()
                lastSuccess()
                lastFailure()
                lastDuration()
                buildButton()
            }

            //TODO: reorganize constructor to be able to provide RegexMatchValue
            if (!"${descFilter}".isEmpty()) {
                jobFilters {
                    regex {
                        matchType(MatchType.INCLUDE_MATCHED)
                        matchValue(RegexMatchValue.DESCRIPTION)
                        regex("${descFilter}")
                    }
                }
            }

            if (!"${nameFilter}".isEmpty()) {
                jobFilters {
                    regex {
                        matchType(MatchType.INCLUDE_MATCHED)
                        matchValue(RegexMatchValue.NAME)
                        regex("${nameFilter}")
                    }
                }
            }
        }
        return view
    }

}