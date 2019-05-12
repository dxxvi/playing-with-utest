package twitter

import org.junit.jupiter.api.Test

/**
  * Input: list of roman characters, e.g. List(X, M, I, D, M)
  * Inspect that list pairwise: if the 1st character < the 2nd, transform that pair to ExprPlus, otherwise ExprMinus;
  *   if there's 1 character left, transform it to ExprLit.
  * Output: list of the above Expr
  */
class HichicClass {
    @Test
    def test(): Unit = {
        abstract class ARoman(val symbol: Char, val value: Int)
        case object RSymI extends ARoman('I', 1)
        case object RSymV extends ARoman('V', 5)
        case object RSymX extends ARoman('X', 10)
        case object RSymL extends ARoman('L', 50)
        case object RSymC extends ARoman('C', 100)
        case object RSymD extends ARoman('D', 500)
        case object RSymM extends ARoman('M', 1000)

        sealed trait Expr[A]
        case class ExprLit[A](value: A) extends Expr[A]
        case class ExprPlus[A](a: A, b: A) extends Expr[A]
        case class ExprMinus[A](a: A, b: A) extends Expr[A]

        def transform(input: List[ARoman]): Iterator[Expr[ARoman]] = input.grouped(2) map {
            case head::Nil => ExprLit(head)
            case head::tail if head.value <= tail.head.value => ExprPlus(head, tail.head)
            case head::tail => ExprMinus(head, tail.head)
        }

        println(transform(List(RSymX, RSymM, RSymI, RSymD, RSymM)).toList)
        // List(ExprPlus(RSymX,RSymM), ExprPlus(RSymI,RSymD), ExprLit(RSymM))
        println(transform(List(RSymX, RSymM, RSymI, RSymD, RSymM, RSymC, RSymL, RSymV)).toList)
        // List(ExprPlus(RSymX,RSymM), ExprPlus(RSymI,RSymD), ExprMinus(RSymM,RSymC), ExprMinus(RSymL,RSymV))
    }
}
