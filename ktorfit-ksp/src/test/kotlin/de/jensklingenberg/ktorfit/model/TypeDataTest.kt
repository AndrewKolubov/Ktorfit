package de.jensklingenberg.ktorfit.model

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TypeDataTest{

    fun mocki(s: String): KSClassDeclaration {
        val classDec : KSClassDeclaration = mock()
        return classDec
    }

    @Test
    fun getmyTpye(){
        val resolver : Resolver = mock()
        val o = object : KSName{
            override fun asString(): String {
               return this.toString()
            }

            override fun getQualifier(): String {
                return this.toString()
            }

            override fun getShortName(): String {
                return this.toString()
            }

        }
        whenever(resolver.getClassDeclarationByName(o)).thenAnswer{}
       val tt= TypeData.getMyType("Map<String,Test>", listOf("kotlin.String","com.example.Test"),"de.test",{mocki(it)})
        Assert.assertEquals("Map",tt.qualifiedName)
        Assert.assertEquals("kotlin.String", tt.typeArgs[0].qualifiedName)
        Assert.assertEquals("com.example.Test", tt.typeArgs[1].qualifiedName)


    }
}