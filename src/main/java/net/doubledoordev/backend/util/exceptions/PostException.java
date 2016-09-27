/*
 * D3Backend
 * Copyright (C) 2015 - 2016  Dries007 & Double Door Development
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.doubledoordev.backend.util.exceptions;

/**
 * @author Dries007
 */
public class PostException extends RuntimeException
{
    public PostException()
    {
    }

    public PostException(String message)
    {
        super(message);
    }

    public PostException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public PostException(Throwable cause)
    {
        super(cause);
    }

    public PostException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
    {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}