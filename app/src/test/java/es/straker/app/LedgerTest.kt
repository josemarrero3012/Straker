package es.straker.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],manifest=Config.NONE)
class LedgerTest {
    @Test fun ledgerLifecycle()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val owner=uid()
        val repo=LedgerRepository(ctx,owner)
        repo.create("Principal","Casa",true,"100")
        repo.create("Banco","",false,"0")
        val initial=repo.state.first()
        val a=initial.banks.single{it.book}.id
        val b=initial.banks.single{!it.book}.id
        repo.wager(a,"Partido","","Fútbol","10","1,85")
        val bet=repo.state.first().bets.single()
        for((status,balance) in listOf("Pendiente" to 9000L,"Ganada" to 10850L,"Perdida" to 9000L,"Nula" to 10000L,"Cash out" to 9750L)){
            repo.settle(bet.id,status,"7,50")
            assertEquals(balance,repo.state.first().balance(a))
        }
        repo.move("Traspaso",a,b,"25","")
        repo.move("Transferencia",b,a,"5","")
        repo.move("Ingreso","",b,"7","")
        repo.move("Retirada",b,"","3","")
        assertTrue(runCatching{repo.move("Retirada",b,"","999","")}.isFailure)
        repo.close()
        val reopened=LedgerRepository(ctx,owner)
        assertEquals(7750L,reopened.state.first().balance(a))
        assertEquals(2400L,reopened.state.first().balance(b))
        reopened.close()
        val other=LedgerRepository(ctx,uid())
        assertTrue(other.state.first().banks.isEmpty())
        other.close()
    }
}
