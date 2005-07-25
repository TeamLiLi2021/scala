/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id$


package scala.tools.nsc.backend.icode;

import scala.tools.nsc.ast._;
import scala.tools.nsc.backend.icode.Primitives._;


/**
 * The ICode intermediate representation. It is a stack-based
 * representation, very close to the JVM and .NET. It uses the
 * erased types of Scala and references Symbols to refer named entities
 * in the source files.
 */
abstract class Opcodes: Global {

  /** This class represents an instruction of the intermediate code.
   *  Each case subclass will represent a specific operation.
   */
  abstract class Instruction {

    /** This abstract method returns the number of used elements on the stack */
    def consumed : Int = 0;

    /** This abstract method returns the number of produced elements on the stack */
    def produced : Int = 0;

    /** This method returns the difference of size of the stack when the instruction is used */
    def difference = produced-consumed;
  }

  object opcodes {

    /** Loads the "this" references on top of the stack.
     * Stack: ...
     *    ->: ...:ref
     */
    case class THIS(clasz: Symbol) extends Instruction {
      /** Returns a string representation of this constant */
      override def toString(): String = "THIS";

      override def consumed = 0;
      override def produced = 1;
    }

    /** Loads a constant on the stack.
     * Stack: ...
     *    ->: ...:constant
     */
    case class CONSTANT(constant: Constant) extends Instruction{
      /** Returns a string representation of this constant */
      override def toString(): String = "CONSTANT ("+constant.toString()+")";

      override def consumed = 0;
      override def produced = 1;
    }

    /** Loads an element of an array. The array and the index should
     * be on top of the stack.
     * Stack: ...:array[a](Ref):index(Int)
     *    ->: ...:element(a)
     */
    case class LOAD_ARRAY_ITEM() extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "LOAD_ARRAY_ITEM";

      override def consumed = 2;
      override def produced = 1;
    }

    /** Load a local variable on the stack. It can be a method argument.
     * Stack: ...
     *    ->: ...:value
     */
    case class LOAD_LOCAL(local: Symbol, isArgument: boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "LOAD_LOCAL "+local.toString(); //+isArgument?" (argument)":"";

      override def consumed = 0;
      override def produced = 1;
    }

    /** Load a field on the stack. The object to which it refers should be
     * on the stack.
     * Stack: ...:ref
     *    ->: ...:value
     */
    case class LOAD_FIELD(field: Symbol, isStatic: boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "LOAD_FIELD "+field.toString(); //+isStatic?" (static)":"";

      override def consumed = 1;
      override def produced = 1;
    }

    /** Store a value into an array at a specified index.
     * Stack: ...:array[a](Ref):index(Int):value(a)
     *    ->: ...
     */
    case class STORE_ARRAY_ITEM() extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "STORE_ARRAY_ITEM";

      override def consumed = 3;
      override def produced = 0;
    }

    /** Store a value into a local variable. It can be an argument.
     * Stack: ...:value
     *    ->: ...
     */
    case class STORE_LOCAL(local: Symbol, isArgument: boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "STORE_LOCAL "+local.toString(); //+isArgument?" (argument)":"";

      override def consumed = 1;
      override def produced = 0;
    }

    /** Store a value into a field.
     * Stack: ...:ref:value
     *    ->: ...
     */
    case class STORE_FIELD(field: Symbol, isStatic: boolean) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "STORE_FIELD "+field.toString(); //+isStatic?" (static)":"";

      override def consumed = 2;
      override def produced = 0;
    }

    /** Call a primitive function.
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:result
     */
    case class CALL_PRIMITIVE(primitive: Primitive) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CALL "+primitive.toString();

      override def consumed = primitive match {
        case (Negation(_)) => 1;
        case (Test(_,_,true)) => 1;
        case (Test(_,_,false)) => 2;
        case (Comparison(_,_)) => 2;
        case (Arithmetic(_,_)) => 2;
        case (Logical(_,_)) => 2;
        case (Shift(_,_)) => 2;
        case (Conversion(_,_)) => 1;
        case (ArrayLength(_)) => 1;
        case (StringConcat(_,_)) => 2;
      }
      override def produced = 1;
    }

    /** This class represents a CALL_METHOD instruction
     * STYLE: dynamic / static(StaticInstance)
     * Stack: ...:ref:arg1:arg2:...:argn
     *    ->: ...:result
     *
     * STYLE: new - unused by jvm
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:ref
     *
     * STYLE: static(StaticClass)
     * Stack: ...:arg1:arg2:...:argn
     *    ->: ...:result
     *
     */
    case class CALL_METHOD(method: Symbol, style: InvokeStyle) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CALL_METHOD "+method.toString()+" ("+style.toString()+")";

      override def consumed = {
        var result = method.tpe.paramTypes.length;
        result = result + (style match {
          case Dynamic => 1
          case Static(true) => 1
          case _ => 0
        });

        result;
      }
      override def produced = 1;
    }

    /** Create a new instance of a class.
     * Stack: ...:
     *    ->: ...:ref
     */
    case class NEW(clasz: Symbol) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String = "NEW "+clasz.toString();

      override def consumed = 0;
      override def produced = 1;
    }


    /** This class represents a CREATE_ARRAY instruction
     * Stack: ...:size(int)
     *    ->: ...:arrayref
     */
    case class CREATE_ARRAY(element: Type) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CREATE_ARRAY "+element.toString();

      override def consumed = 1;
      override def produced = 1;
    }

    /** This class represents a IS_INSTANCE instruction
     * Stack: ...:ref
     *    ->: ...:result(boolean)
     */
    case class IS_INSTANCE(typ: Type) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="IS_INSTANCE "+typ.toString();

      override def consumed = 1;
      override def produced = 1;
    }

    /** This class represents a CHECK_CAST instruction
     * Stack: ...:ref(oldtype)
     *    ->: ...:ref(typ <=: oldtype)
     */
    case class CHECK_CAST(typ: Type) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CHECK_CAST "+typ.toString();

      override def consumed = 1;
      override def produced = 1;
    }

    /** This class represents a SWITCH instruction
     * Stack: ...:index(int)
     *    ->: ...:
     */
    case class SWITCH(tags: Array[Array[int]], labels: List[BasicBlock]) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="SWITCH ...";

      override def consumed = 1;
      override def produced = 0;
    }

    /** This class represents a JUMP instruction
     * Stack: ...
     *    ->: ...
     */
    case class JUMP(where: BasicBlock) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="JUMP "+where.label;

      override def consumed = 0;
      override def produced = 0;
    }

    /** This class represents a CJUMP instruction
     * It compares the two values on the stack with the 'cond' test operator
     * Stack: ...:value1:value2
     *    ->: ...
     */
    case class CJUMP(successBlock: BasicBlock,
		     failureBlock: BasicBlock,
		     cond: TestOp) extends Instruction {

                       /** Returns a string representation of this instruction */
                       override def toString(): String ="CJUMP "+cond.toString()+" ? "+successBlock.label+" : "+failureBlock.label;

                       override def consumed = 2;
                       override def produced = 0;
                     }

    /** This class represents a CZJUMP instruction
     * It compares the one value on the stack and zero with the 'cond' test operator
     * Stack: ...:value:
     *    ->: ...
     */
    case class CZJUMP(successBlock: BasicBlock, failureBlock: BasicBlock, cond: TestOp) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="CZJUMP "+cond.toString()+" ? "+successBlock.label+" : "+failureBlock.label;

      override def consumed = 1;
      override def produced = 0;
    }


    /** This class represents a RETURN instruction
     * Stack: ...
     *    ->: ...
     */
    case class RETURN() extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="RETURN";

      override def consumed = 0;
      override def produced = 0;
    }

    /** This class represents a THROW instruction
     * Stack: ...:Throwable(Ref)
     *    ->: ...:
     */
    case class THROW() extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="THROW";

      override def consumed = 1;
      override def produced = 0;
    }

    /** This class represents a DROP instruction
     * Stack: ...:something
     *    ->: ...
     */
    case class DROP (typ: Type) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="DROP "+typ.toString();

      override def consumed = 1;
      override def produced = 0;
    }

    /** This class represents a DUP instruction
     * Stack: ...:something
     *    ->: ...:something:something
     */
    case class DUP (typ: Type) extends Instruction {
      /** Returns a string representation of this instruction */
      override def toString(): String ="DUP";

      override def consumed = 1;
      override def produced = 2;
    }

    /** This class represents a MONITOR_ENTER instruction
     * Stack: ...:object(ref)
     *    ->: ...:
     */
    case class MONITOR_ENTER() extends Instruction {

      /** Returns a string representation of this instruction */
      override def toString(): String ="MONITOR_ENTER";

      override def consumed = 1;
      override def produced = 0;
    }

    /** This class represents a MONITOR_EXIT instruction
     * Stack: ...:object(ref)
     *    ->: ...:
     */
    case class MONITOR_EXIT() extends Instruction {

      /** Returns a string representation of this instruction */
      override def toString(): String ="MONITOR_EXIT";

      override def consumed = 1;
      override def produced = 0;
    }

    /** This class represents a method invocation style. */
    trait InvokeStyle {
      /** Is this a new object creation? */
      def isNew: Boolean = this match {
        case NewInstance =>  true;
        case _   =>  false;
      }

      /** Is this a dynamic method call? */
      def isDynamic: Boolean = this match {
        case Dynamic =>  true;
        case _       => false;
      }

      /** Is this a static method call? */
      def isStatic: Boolean = this match {
        case Static(_) => true;
        case _ =>  false;
      }

      /** Is this an instance method call? */
      def hasInstance: Boolean = this match {
        case Dynamic => true;
        case Static(onInstance) => onInstance;
        case _ => false;
      }

      /** Returns a string representation of this style. */
      override def toString(): String = this match {
        case NewInstance =>  "new";
        case Dynamic =>  "dynamic";
        case Static(false) => "static-class";
        case Static(true) =>  "static-instance";
        case SuperCall(mixin) => "super(" + mixin + ")";
      }
    }

    case object NewInstance extends InvokeStyle;
    case object Dynamic extends InvokeStyle;

    /**
     * Special invoke. Static(true) is used for constructor,
     * priavate and super calls.
     */
    case class Static(onInstance: Boolean) extends InvokeStyle;

    /** Call through super[mixin]. */
    case class SuperCall(mixin: Name) extends InvokeStyle;

  }
}
