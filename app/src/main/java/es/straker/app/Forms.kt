package es.straker.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun Choice(label:String,options:List<String>,selected:String,change:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Column{Caption(label);Box{
        OutlinedButton({open=true},Modifier.fillMaxWidth()){Text(selected.ifBlank{"Seleccionar"}+" ▾")}
        DropdownMenu(open,{open=false}){options.forEach{value->DropdownMenuItem(text={Text(value)},onClick={change(value);open=false})}}
    }}
}
@Composable fun Field(label:String,value:String,decimal:Boolean=false,onChange:(String)->Unit){
    OutlinedTextField(value,{if(it.length<=120)onChange(it)},Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun FormPage(title:String,vm:LedgerViewModel,back:()->Unit,content:@Composable ()->Unit){
    val error by vm.error.collectAsStateWithLifecycle()
    Page(title,back){content();if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)}
}
@Composable fun Save(vm:LedgerViewModel,text:String,done:()->Unit,action:suspend LedgerRepository.()->Unit){
    val saving by vm.saving.collectAsStateWithLifecycle()
    Button({vm.save(done,action)},Modifier.fillMaxWidth(),enabled=!saving){Text(if(saving)"Guardando…" else text)}
}
@Composable fun BankForm(vm:LedgerViewModel,done:()->Unit){
    var type by rememberSaveable{mutableStateOf("Banca de apuestas")}
    var name by rememberSaveable{mutableStateOf("")};var house by rememberSaveable{mutableStateOf("")}
    var amount by rememberSaveable{mutableStateOf("")}
    Choice("Tipo de cuenta",listOf("Banca de apuestas","Cuenta bancaria"),type){type=it}
    Field("Nombre de la cuenta",name){name=it}
    if(type=="Banca de apuestas")Field("Casa de apuestas",house){house=it}
    Field("Saldo inicial (€)",amount,true){amount=it}
    Save(vm,"Crear cuenta",done){create(name,house,type=="Banca de apuestas",amount)}
}
@Composable fun BetForm(vm:LedgerViewModel,book:Book,done:()->Unit,addBank:()->Unit){
    val accounts=book.banks.filter{it.book}
    if(accounts.isEmpty()){Caption("Crea una banca de apuestas para registrar el importe jugado.");Button(addBank){Text("Crear banca")};return}
    var name by rememberSaveable{mutableStateOf(accounts.first().name)}
    var event by rememberSaveable{mutableStateOf("")};var pick by rememberSaveable{mutableStateOf("")}
    var sport by rememberSaveable{mutableStateOf("Fútbol")};var stake by rememberSaveable{mutableStateOf("")};var price by rememberSaveable{mutableStateOf("")}
    Field("Evento",event){event=it};Field("Selección (opcional)",pick){pick=it}
    Choice("Deporte",listOf("Fútbol","Baloncesto","Tenis","Esports","Multideporte","Otro"),sport){sport=it}
    Choice("Banca",accounts.map{it.name},name){name=it}
    val id=accounts.firstOrNull{it.name==name}?.id.orEmpty()
    Caption("Disponible: "+eur(book.balance(id)))
    Field("Importe (€)",stake,true){stake=it};Field("Cuota decimal",price,true){price=it}
    Save(vm,"Registrar apuesta",done){wager(id,event,pick,sport,stake,price)}
}
@Composable fun MoveForm(vm:LedgerViewModel,book:Book,done:()->Unit,addBank:()->Unit){
    var kind by rememberSaveable{mutableStateOf("Ingreso")}
    Choice("Tipo de movimiento",moveTypes,kind){kind=it}
    val names=book.banks.map{it.name}
    if(names.isEmpty() || (kind in listOf("Traspaso","Transferencia") && names.size<2)){
        Caption("Añade una cuenta para este movimiento.");Button(addBank){Text("Crear cuenta")};return
    }
    var from by rememberSaveable{mutableStateOf(names.first())};var to by rememberSaveable{mutableStateOf(names.last())}
    var amount by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")}
    if(kind!="Ingreso")Choice("Origen",names,from){from=it}
    val possible=if(kind=="Ingreso")names else names.filter{it!=from}
    val selected=to.takeIf{it in possible}?:possible.firstOrNull().orEmpty()
    if(kind!="Retirada")Choice("Destino",possible,selected){to=it}
    Field("Importe (€)",amount,true){amount=it};Field("Concepto (opcional)",note){note=it}
    Save(vm,"Registrar movimiento",done){move(kind,book.banks.find{it.name==from}?.id.orEmpty(),book.banks.find{it.name==selected}?.id.orEmpty(),amount,note)}
}
@Composable fun SettleForm(vm:LedgerViewModel,b:Bet,book:Book,done:()->Unit){
    var state by rememberSaveable(b.id){mutableStateOf(b.status)}
    var cash by rememberSaveable(b.id){mutableStateOf(if(b.status=="Cash out")java.math.BigDecimal.valueOf(b.cash,2).toPlainString() else "")}
    CardBox{Title(b.event,27);Caption(b.sport+" · "+book.name(b.bank));if(b.pick.isNotBlank())Text(b.pick)
        Text("Importe "+eur(b.stake)+" · Cuota "+odds(b.odds));Text(b.status)
        Caption(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(b.at)))
        if(b.status!="Pendiente"){Text("Retorno "+eur(b.payout()));Caption("Resultado neto "+eur(b.payout()-b.stake))}
    }
    Choice("Estado de la apuesta",outcomes,state){state=it}
    if(state=="Cash out")Field("Retorno total del cash out (€)",cash,true){cash=it}
    Save(vm,"Guardar estado",done){settle(b.id,state,cash)}
}
