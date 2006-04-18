package org.drools.decisiontable.model;
/*
 * Copyright 2005 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */








/**
 * @author <a href="mailto:michael.neale@gmail.com"> Michael Neale </a>
 * 
 * Represents an import (nominally at the rule-set level). The idea of this can
 * be extended to other ruleset level settings.
 */
public class Import extends DRLElement
    implements
    DRLJavaEmitter
{

    private String className;

    /**
     * @return Returns the className.
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @param className
     *            The className to set.
     */
    public void setClassName(String clazz)
    {
        className = clazz;
    }

	public void renderDRL(DRLOutput out) {
		out.writeLine("import " + className + ";");
	}
}