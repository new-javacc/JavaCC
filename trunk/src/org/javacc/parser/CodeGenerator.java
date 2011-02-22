package org.javacc.parser;

import static org.javacc.parser.JavaCCGlobals.addUnicodeEscapes;
import java.io.*;
import java.util.List;

public class CodeGenerator {
  protected StringBuffer mainBuffer = new StringBuffer();
  protected StringBuffer includeBuffer = new StringBuffer();
  protected StringBuffer staticsBuffer = new StringBuffer();
  protected StringBuffer outputBuffer = mainBuffer;

  public void genCodeLine(Object... code) {
    genCode(code);
    genCode("\n");
  }

  public void genCode(Object... code) {
    for (Object s: code) {
      outputBuffer.append(s);
    }
  }

  public void saveOutput(String fileName) {
    if (!isJavaLanguage()) {
      String incfileName = fileName.replace(".cc", ".h");
      includeBuffer.insert(0, "#define " + new File(incfileName).getName().replace('.', '_').toUpperCase() + "\n");
      includeBuffer.insert(0, "#ifndef " + new File(incfileName).getName().replace('.', '_').toUpperCase() + "\n");
      includeBuffer.append("#endif\n");
      saveOutput(incfileName, includeBuffer);

      // dump the statics into the main file with the code.
      mainBuffer.insert(0, staticsBuffer);
      mainBuffer.insert(0, "#include \"" + incfileName + "\"\n");
    }

    mainBuffer.insert(0, "/* " + new File(fileName).getName() + " */\n");
    saveOutput(fileName, mainBuffer);
  }

  public void saveOutput(String fileName, StringBuffer sb) {
    PrintWriter fw = null;
    try {
      File tmp = new File(fileName);
      fw = new PrintWriter(
              new BufferedWriter(
              new FileWriter(tmp),
              8092
          )
      );

      fw.print(sb.toString());
    } catch(IOException ioe) {
      JavaCCErrors.fatal("Could not create output file: " + fileName);
    } finally {
      if (fw != null) {
        fw.close();
      }
    }
  }

  protected int cline, ccol;

  protected void printTokenSetup(Token t) {
    Token tt = t;
    while (tt.specialToken != null) tt = tt.specialToken;
    cline = tt.beginLine;
    ccol = tt.beginColumn;
  }

  protected void printTokenList(List list) {
    Token t = null;
    for (java.util.Iterator it = list.iterator(); it.hasNext();) {
      t = (Token)it.next();
      printToken(t);
    }
    
    if (t != null)
      printTrailingComments(t);
  }

  protected void printTokenOnly(Token t) {
    genCode(getStringForTokenOnly(t));
  }

  protected String getStringForTokenOnly(Token t) {
    String retval = "";
    for (; cline < t.beginLine; cline++) {
      retval += "\n"; ccol = 1;
    }
    for (; ccol < t.beginColumn; ccol++) {
      retval += " ";
    }
    if (t.kind == JavaCCParserConstants.STRING_LITERAL ||
        t.kind == JavaCCParserConstants.CHARACTER_LITERAL)
       retval += addUnicodeEscapes(t.image);
    else
       retval += t.image;
    cline = t.endLine;
    ccol = t.endColumn+1;
    if (t.image.length() > 0) {
      char last = t.image.charAt(t.image.length()-1);
      if (last == '\n' || last == '\r') {
        cline++; ccol = 1;
      }
    } 

    return retval;
  }

  protected void printToken(Token t) {
    genCode(getStringToPrint(t));
  }

  protected String getStringToPrint(Token t) {
    String retval = "";
    Token tt = t.specialToken;
    if (tt != null) {
      while (tt.specialToken != null) tt = tt.specialToken;
      while (tt != null) {
        retval += getStringForTokenOnly(tt);
        tt = tt.next;
      }
    }

    return retval + getStringForTokenOnly(t);
  }

  protected void printLeadingComments(Token t) {
    genCode(getLeadingComments(t));
  }

  protected String getLeadingComments(Token t) {
    String retval = "";
    if (t.specialToken == null) return retval;
    Token tt = t.specialToken;
    while (tt.specialToken != null) tt = tt.specialToken;
    while (tt != null) {
      retval += getStringForTokenOnly(tt);
      tt = tt.next;
    }
    if (ccol != 1 && cline != t.beginLine) {
      retval += "\n";
      cline++; ccol = 1;
    }

    return retval;
  }

  protected void printTrailingComments(Token t) {
    outputBuffer.append(getTrailingComments(t));
  }

  protected String getTrailingComments(Token t) {
    if (t.next == null) return "";
    return getLeadingComments(t.next);
  }

  /**
   * for testing
   */
  public String getGeneratedCode() {
    return outputBuffer.toString() + "\n";
  }

  /**
   * Generate annotation. @XX syntax for java, comments in C++
   */
  public void genAnnotation(String ann) {
    if (Options.getOutputLanguage().equals("java")) {
      genCode("@" + ann);
    } else { // For now, it's only C++ for now
      genCode( "/*" + ann + "*/");
    }
  }

  /**
   * Generate a modifier
   */
  public void genModifier(String mod) {
    String origMod = mod.toLowerCase();
    if (isJavaLanguage()) {
      genCode(mod);
    } else { // For now, it's only C++ for now
      if (origMod.equals("public") || origMod.equals("private")) {
        genCode(origMod + ": ");
      }
      // we don't care about other mods for now.
    }
  }

  /**
   * Generate a class with a given name, an array of superclass and
   * another array of super interfaes
   */
  public void genClassStart(String mod, String name, String[] superClasses, String[] superInterfaces) {
    if (isJavaLanguage() && mod != null) {
       genModifier(mod);
    }
    genCode("class " + name);
    if (isJavaLanguage()) {
      if (superClasses.length == 1) {
        genCode(" extends " + superClasses[0]);
      }
      if (superInterfaces.length != 0) {
        genCode(" implements ");
      }
    } else {
      if (superClasses.length > 0 || superInterfaces.length > 0) {
        genCode(" : ");
      }
 
      genCommaSeperatedString(superClasses);
    }

    genCommaSeperatedString(superInterfaces);
    genCodeLine(" {");
    if (!isJavaLanguage()) {
      genCodeLine("   public:");
    }
  }

  private void genCommaSeperatedString(String[] strings) {
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) {
        genCode(", ");
      }

      genCode(strings[i]);
    }
  }

  protected boolean isJavaLanguage() {
    return Options.getOutputLanguage().equals("java");
  }

  public void switchToMainFile() {
    outputBuffer = mainBuffer; 
  }

  public void switchToStaticsFile() {
    if (!isJavaLanguage()) {
      outputBuffer = staticsBuffer; 
    }
  }

  public void switchToIncludeFile() {
    if (!isJavaLanguage()) {
      outputBuffer = includeBuffer; 
    }
  }

  public void generateMethodDefHeader(String modsAndRetType, String className, String nameAndParams) {
    generateMethodDefHeader(modsAndRetType, className, nameAndParams, null);
  }

  public void generateMethodDefHeader(String modsAndRetType, String className, String nameAndParams, String exceptions) {
    // for C++, we generate the signature in the header file and body in main file
    if (isJavaLanguage()) {
      genCode(modsAndRetType + " " + nameAndParams);
      if (exceptions != null) {
        genCode(" throws " + exceptions);
      }
      genCodeLine("");
    } else {
      includeBuffer.append("\n" + modsAndRetType + " " + nameAndParams);
      if (exceptions != null)
        includeBuffer.append(" throw(" + exceptions + ")");
      includeBuffer.append(";\n");

      mainBuffer.append("\n" + modsAndRetType + " " +
                           getClassQualifier(className) + nameAndParams);
      if (exceptions != null)
        mainBuffer.append(" throw( " + exceptions + ")");
      switchToMainFile();
    }
  }

  protected String getClassQualifier(String className) {
    return className == null ? "" : className + "::";
  }
}
