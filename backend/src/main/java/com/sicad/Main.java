package com.sicad;
import com.sicad.socket.Servidor;
import com.sicad.database.ExecutorMigracao;
public class Main
{
    public static void main( String[] args )
    {
        System.out.println( "Iniciando servidor" );
        ExecutorMigracao.rodarMigrations();
        Servidor.main(args);
    }
}
