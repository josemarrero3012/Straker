package es.straker.app

import android.content.Context
import androidx.room.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun uid() = UUID.randomUUID().toString()
@Entity data class Bank(@PrimaryKey val id:String=uid(), val name:String, val house:String, val book:Boolean)
@Entity data class Bet(@PrimaryKey val id:String=uid(), val bank:String, val event:String, val pick:String, val sport:String,
    val stake:Long, val odds:Long, val status:String="Pendiente", val cash:Long=0, val at:Long=System.currentTimeMillis())
@Entity data class Movement(@PrimaryKey val id:String=uid(), val kind:String, val origin:String?, val dest:String?,
    val amount:Long, val note:String="", val at:Long=System.currentTimeMillis())

@Dao interface LedgerDao {
    @Query("SELECT * FROM Bank ORDER BY rowid DESC") suspend fun banks():List<Bank>
    @Query("SELECT * FROM Bet ORDER BY at DESC,rowid DESC") suspend fun bets():List<Bet>
    @Query("SELECT * FROM Movement ORDER BY at DESC,rowid DESC") suspend fun moves():List<Movement>
    @Query("SELECT (SELECT COUNT(*) FROM Bank)+(SELECT COUNT(*) FROM Bet)+(SELECT COUNT(*) FROM Movement)") fun changes():Flow<Long>
    @Insert suspend fun insert(bank:Bank)
    @Insert suspend fun insert(bet:Bet)
    @Insert suspend fun insert(movement:Movement)
    @Update suspend fun update(bet:Bet)
}
@Database(entities=[Bank::class,Bet::class,Movement::class],version=1,exportSchema=false)
abstract class LedgerDb: RoomDatabase() { abstract fun dao():LedgerDao }
val outcomes=listOf("Pendiente","Ganada","Perdida","Nula","Cash out")
val moveTypes=listOf("Ingreso","Traspaso","Transferencia","Retirada")
fun number(raw:String, scale:Int):Long {
    val text=raw.trim().replace(',','.')
    require(text.matches(Regex("[0-9]{1,10}([.][0-9]{1,$scale})?"))) { "Revisa el importe o la cuota." }
    return BigDecimal(text).movePointRight(scale).longValueExact().also { require(it<=99999999999L){"La cifra supera el límite."} }
}
fun Bet.payout()=when(status){
    "Ganada"->BigDecimal.valueOf(stake).multiply(BigDecimal.valueOf(odds,3)).setScale(0,RoundingMode.HALF_UP).longValueExact()
    "Nula"->stake
    "Cash out"->cash
    else->0L
}
data class Book(val banks:List<Bank> = emptyList(),val bets:List<Bet> = emptyList(),val moves:List<Movement> = emptyList()){
    fun balance(id:String)=moves.sumOf{(if(it.dest==id)it.amount else 0L)-(if(it.origin==id)it.amount else 0L)} +
        bets.filter{it.bank==id}.sumOf{it.payout()-it.stake}
    fun name(id:String?)=banks.find{it.id==id}?.name?:"Exterior"
}
class LedgerRepository(context:Context, owner:String){
    private val key=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString(""){"%02x".format(it)}
    private val db=Room.databaseBuilder(context.applicationContext,LedgerDb::class.java,"ledger-$key.db").build()
    private val dao=db.dao()
    private suspend fun snapshot()=Book(dao.banks(),dao.bets(),dao.moves())
    val state=dao.changes().map{db.withTransaction{snapshot()}}
    fun close()=db.close()
    suspend fun create(name:String, house:String, book:Boolean, opening:String)=db.withTransaction{
        require(name.isNotBlank() && (!book || house.isNotBlank())){"Introduce el nombre y la casa de apuestas."}
        require(dao.banks().none{it.name.equals(name.trim(),true)}){"Ya existe una cuenta con ese nombre."}
        val amount=number(opening.ifBlank{"0"},2)
        val bank=Bank(name=name.trim(),house=if(book)house.trim() else "",book=book)
        dao.insert(bank)
        if(amount>0) dao.insert(Movement(kind="Saldo inicial",origin=null,dest=bank.id,amount=amount))
    }
    suspend fun wager(bank:String,event:String,pick:String,sport:String,amount:String,price:String)=db.withTransaction{
        val b=snapshot()
        require(b.banks.any{it.id==bank && it.book} && event.isNotBlank()){"Elige una banca e introduce el evento."}
        val stake=number(amount,2); val odds=number(price,3)
        require(stake>0 && odds>=1000){"El importe debe ser positivo y la cuota al menos 1."}
        require(BigDecimal.valueOf(stake)*BigDecimal.valueOf(odds,3)<=BigDecimal("99999999999")){"El retorno supera el límite."}
        require(b.balance(bank)>=stake){"Saldo insuficiente."}
        dao.insert(Bet(bank=bank,event=event.trim(),pick=pick.trim(),sport=sport,stake=stake,odds=odds))
    }
    suspend fun settle(id:String,status:String,cash:String)=db.withTransaction{
        val b=snapshot(); val old=requireNotNull(b.bets.find{it.id==id})
        require(status in outcomes)
        val updated=old.copy(status=status,cash=if(status=="Cash out")number(cash,2) else 0)
        require(b.balance(old.bank)-old.payout()+updated.payout()>=0){"El cambio dejaría la banca en negativo."}
        dao.update(updated)
    }
    suspend fun move(kind:String,from:String,to:String,raw:String,note:String)=db.withTransaction{
        val b=snapshot(); val src=from.takeUnless{kind=="Ingreso"}; val dst=to.takeUnless{kind=="Retirada"}
        val amount=number(raw,2)
        require(kind in moveTypes && amount>0){"Introduce un importe positivo."}
        require(src!=dst && listOfNotNull(src,dst).all{id->b.banks.any{it.id==id}}){"Selecciona cuentas válidas y distintas."}
        require(src==null || b.balance(src)>=amount){"Saldo insuficiente en el origen."}
        dao.insert(Movement(kind=kind,origin=src,dest=dst,amount=amount,note=note.trim()))
    }

}
class LedgerViewModel(val repo:LedgerRepository):ViewModel(){
    val book=repo.state.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),Book())
    val error=MutableStateFlow("")
    val saving=MutableStateFlow(false)
    fun save(done:()->Unit, action:suspend LedgerRepository.()->Unit){
        if(saving.value)return
        saving.value=true;error.value=""
        viewModelScope.launch {
            try{repo.action();done()}catch(e:IllegalArgumentException){error.value=e.message?:"Comprueba los campos."}
            catch(e:android.database.SQLException){error.value="No se ha podido guardar en el dispositivo."}
            finally{saving.value=false}
        }
    }
    override fun onCleared(){repo.close()}
}
