package com.sicad;
import com.sicad.socket.Servidor;
import com.sicad.database.MigrationRunner;
/**
 * Hello world!
 * test da api do github
 */
public class Main 
{
    public static void main( String[] args )
    {
        System.out.println( "Iniciando servidor" );
        MigrationRunner.rodarMigrations();
        Servidor.main(args);
    }
}
